package com.casegen.generation.backend.service;

import static com.casegen.generation.backend.dto.GenerationDtos.*;

import com.casegen.generation.backend.repository.GenerationRunRepository;
import com.casegen.generation.logical.dto.LogicalModelDtos;
import com.casegen.generation.logical.service.LogicalModelService;
import com.casegen.project.service.ProjectAuthorizationService;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GenerationService {
    public static final String VERSION = "10.2.3";
    private final GenerationRunRepository runs;
    private final LogicalModelService logical;
    private final ProjectAuthorizationService auth;
    private final SpringBackendRenderer renderer;
    private final IsolatedPostgresVerifier postgres;
    private final Executor executor;
    private final Path runtime;
    private final Duration timeout;

    public GenerationService(GenerationRunRepository runs, LogicalModelService logical,
            ProjectAuthorizationService auth, SpringBackendRenderer renderer,
            IsolatedPostgresVerifier postgres, @Qualifier("generationExecutor") Executor executor,
            @Value("${casegen.generation.runtime-root:runtime/generation}") String root,
            @Value("${casegen.generation.process-timeout:PT10M}") Duration timeout) {
        this.runs=runs;this.logical=logical;this.auth=auth;this.renderer=renderer;this.postgres=postgres;
        this.executor=executor;this.runtime=Path.of(root).toAbsolutePath().normalize();this.timeout=timeout;
    }

    public StartResponse generate(UUID logicalId) {
        var source=runs.source(logicalId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"LogicalModel not found"));
        UUID actor=auth.requireGenerate(source.projectId()).getId();
        if(!"VALID".equals(source.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a VALID LogicalModel can be generated");
        // Capture the immutable LogicalModel while the request SecurityContext is available.
        LogicalModelDtos.Response input=logical.get(logicalId);
        UUID id=UUID.randomUUID();
        runs.create(id,source,actor,VERSION);
        try { executor.execute(()->runPipeline(id,input,source)); }
        catch(RuntimeException error){ runs.fail(id,"QUEUED",safe(error)); }
        return new StartResponse(id,"QUEUED");
    }

    public Response get(UUID id) {
        UUID project=runs.project(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Generation not found"));
        auth.requireRead(project);
        return runs.find(id).orElseThrow();
    }

    public List<Response> history(UUID logicalId) {
        var source=runs.source(logicalId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"LogicalModel not found"));
        auth.requireRead(source.projectId());
        return runs.list(logicalId);
    }

    public ArtifactDownload download(UUID runId,UUID artifactId) throws IOException {
        var artifact=runs.storedArtifact(runId,artifactId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Artifact not found in generation"));
        auth.requireGenerate(artifact.projectId());
        if(!"SUCCESS".equals(artifact.runStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only artifacts from a SUCCESS generation can be downloaded");
        Path file=runtime.resolve(artifact.storageKey()).normalize();
        if(!file.startsWith(runtime)||!Files.isRegularFile(file)||Files.isSymbolicLink(file)) throw new ResponseStatusException(HttpStatus.GONE,"Generation artifact is not available");
        try {
            if(Files.size(file)!=artifact.sizeBytes()||!checksum(file).equals(artifact.checksum())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Generation artifact failed its integrity check");
        } catch(ResponseStatusException error){throw error;} catch(Exception error){throw new IOException("Could not verify artifact",error);}
        return new ArtifactDownload(artifact.fileName(),artifact.sizeBytes(),new FileSystemResource(file));
    }

    public record ArtifactDownload(String fileName,long sizeBytes,Resource resource) {}

    void runPipeline(UUID id, LogicalModelDtos.Response input, GenerationRunRepository.Source source) {
        Path workspace=runtime.resolve(id.toString()).resolve("workspace").normalize();
        try {
            if(!workspace.startsWith(runtime)) throw new IOException("Unsafe workspace");
            runs.stage(id,"GENERATING","GENERATING");
            UUID rendering=runs.startTest(id,"GENERATING");
            try {
                renderer.render(workspace,input,new SpringBackendRenderer.Traceability(source.projectId(),source.modelId(),
                        source.modelName(),source.modelVersionId(),source.modelVersionNumber(),source.logicalModelId()));
                validateWorkspace(workspace);runs.finishTest(rendering,true,"Spring Boot workspace rendered.");
            }
            catch(Exception error){runs.finishTest(rendering,false,safe(error));throw error;}

            runs.stage(id,"COMPILING","COMPILING");
            execute(id,workspace,"COMPILING",Map.of(),"compile","-DskipTests");
            var database=postgres.create(id);
            try {
                runs.stage(id,"TESTING","TESTING");
                execute(id,workspace,"TESTING",database.environment(),"test");
                runs.stage(id,"VERIFYING_DATABASE","VERIFYING_DATABASE");
                recordVerification(id);
            } finally { postgres.remove(database); }

            runs.stage(id,"PACKAGING","PACKAGING");
            UUID packaging=runs.startTest(id,"PACKAGING");
            try {
                String rootFolder=safeArtifactId(input.profileSnapshot().path("profile").path("artifactId").asString());
                String fileName=rootFolder+"-v"+source.modelVersionNumber()+".zip";
                Path zip=zip(id,workspace,rootFolder,fileName);
                validateZip(zip,rootFolder);
                runs.artifact(UUID.randomUUID(),id,runtime.relativize(zip).toString().replace('\\','/'),zip.getFileName().toString(),checksum(zip),Files.size(zip));

                Path frontendWorkspace=runtime.resolve(id.toString()).resolve("frontend-workspace").normalize();
                new com.casegen.generation.frontend.service.ReactNativeFrontendRenderer().render(frontendWorkspace,input,
                        new com.casegen.generation.frontend.service.ReactNativeFrontendRenderer.Traceability(source.modelName(),source.modelVersionNumber()));
                String frontendRoot=rootFolder+"-react-native";
                String frontendFile=frontendRoot+"-v"+source.modelVersionNumber()+".zip";
                Path frontendZip=zip(id,frontendWorkspace,frontendRoot,frontendFile);
                runs.artifact(UUID.randomUUID(),id,"REACT_NATIVE_ZIP",runtime.relativize(frontendZip).toString().replace('\\','/'),frontendZip.getFileName().toString(),checksum(frontendZip),Files.size(frontendZip));
                runs.finishTest(packaging,true,fileName+" and "+frontendFile+" packaged and registered.");
            } catch(Exception error){runs.finishTest(packaging,false,safe(error));throw error;}
            runs.success(id);
        } catch(Exception error) {
            runs.fail(id,runs.find(id).map(Response::currentStage).orElse("UNKNOWN"),safe(error));
        }
    }

    private void execute(UUID run,Path dir,String type,Map<String,String> environment,String...goals)throws Exception {
        UUID test=runs.startTest(run,type);List<String>cmd=new ArrayList<>();
        cmd.add(System.getProperty("os.name").toLowerCase().contains("win")?"mvnw.cmd":"./mvnw");cmd.addAll(List.of(goals));
        ProcessBuilder builder=new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);builder.environment().putAll(environment);
        Process process=builder.start();ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=Thread.startVirtualThread(()->{try(var input=process.getInputStream()){input.transferTo(out);}catch(IOException ignored){}});
        boolean done=process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS);if(!done)process.destroyForcibly();reader.join();
        String log=out.toString(java.nio.charset.StandardCharsets.UTF_8);boolean ok=done&&process.exitValue()==0;
        runs.finishTest(test,ok,log);if(!ok)throw new IllegalStateException(type+" failed"+(done?" with exit "+process.exitValue():" by timeout"));
    }
    private void recordVerification(UUID run){UUID test=runs.startTest(run,"VERIFYING_DATABASE");runs.finishTest(test,true,"Fresh isolated PostgreSQL schema: Flyway applied and Spring context loaded with ddl-auto=validate.");}
    private void validateWorkspace(Path root)throws IOException{Path postmanRoot=root.resolve("postman").normalize();try(var paths=Files.walk(root)){for(Path path:paths.toList()){if(!path.normalize().startsWith(root)||Files.isSymbolicLink(path))throw new IOException("Unsafe generated file");if(Files.isRegularFile(path)&&!path.normalize().startsWith(postmanRoot)&&Files.readString(path).contains("{{"))throw new IOException("Unresolved template marker");}}Path postman;try(var files=Files.list(postmanRoot)){postman=files.filter(p->p.getFileName().toString().endsWith(".postman_collection.json")).findFirst().orElseThrow(()->new IOException("Postman collection missing"));}try{tools.jackson.databind.json.JsonMapper.builder().build().readTree(Files.readString(postman));}catch(Exception error){throw new IOException("Postman collection is not valid JSON",error);}}
    private Path zip(UUID id,Path source,String rootFolder,String fileName)throws IOException{Path out=runtime.resolve(id.toString()).resolve("artifacts").resolve(fileName);Files.createDirectories(out.getParent());try(var z=new ZipOutputStream(Files.newOutputStream(out));var paths=Files.walk(source)){for(Path path:paths.filter(Files::isRegularFile).sorted().toList()){String relative=source.relativize(path).toString().replace('\\','/');if(relative.startsWith("target/")||relative.startsWith(".git/")||relative.startsWith("runtime/")||relative.endsWith(".log"))continue;ZipEntry entry=new ZipEntry(rootFolder+"/"+relative);entry.setTime(0);z.putNextEntry(entry);Files.copy(path,z);z.closeEntry();}}return out;}
    private void validateZip(Path zip,String rootFolder)throws IOException{Set<String>entries=new HashSet<>();try(var input=new ZipInputStream(Files.newInputStream(zip))){for(ZipEntry entry;(entry=input.getNextEntry())!=null;)entries.add(entry.getName());}String prefix=rootFolder+"/";for(String required:List.of("pom.xml","mvnw","mvnw.cmd","README.md",".env",".env.example",".gitignore","run.ps1","run.sh","Dockerfile","docker-compose.yml","src/main/resources/application.yml","src/main/resources/db/migration/V1__create_schema.sql"))if(!entries.contains(prefix+required))throw new IOException("ZIP missing required file: "+required);if(entries.stream().noneMatch(e->e.startsWith(prefix+"postman/")&&e.endsWith(".postman_collection.json")))throw new IOException("ZIP missing Postman collection");if(entries.stream().anyMatch(e->e.startsWith(prefix+"target/")||e.startsWith(prefix+"runtime/")||e.startsWith(prefix+".git/")||e.endsWith(".log")))throw new IOException("ZIP contains forbidden runtime content");}
    private String safeArtifactId(String value){String safe=value==null?"":value.replaceAll("[^A-Za-z0-9._-]","-");return safe.isBlank()?"generated-backend":safe;}
    private String checksum(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private String safe(Exception error){String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();return message.length()>2000?message.substring(0,2000):message;}
}
