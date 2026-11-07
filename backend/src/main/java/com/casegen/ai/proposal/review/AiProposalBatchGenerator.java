package com.casegen.ai.proposal.review;

import com.casegen.ai.message.domain.*;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.provider.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiProposalBatchGenerator {
    private final AiMessageRepository messages;private final AiUmlReviewSnapshotBuilder snapshots;
    private final AiProposalBatchPromptBuilder prompts;private final SelectedAiProvider provider;
    private final AiReviewResultParser parser;
    public AiProposalBatchGenerator(AiMessageRepository messages,AiUmlReviewSnapshotBuilder snapshots,
            AiProposalBatchPromptBuilder prompts,SelectedAiProvider provider,AiReviewResultParser parser){this.messages=messages;this.snapshots=snapshots;this.prompts=prompts;this.provider=provider;this.parser=parser;}
    public GeneratedAiReview generate(GenerateAiReviewCommand command){
        if(command==null||command.projectId()==null||command.sessionId()==null||command.sourceMessageId()==null)throw new AiReviewGenerationException("projectId, sessionId and sourceMessageId are required");
        AiMessage source=messages.findDetailedById(command.sourceMessageId()).orElseThrow(()->new AiReviewGenerationException("source message does not exist"));validate(command,source);
        UUID modelId=source.getModel().getId(),diagramId=source.getDiagram()==null?null:source.getDiagram().getId();
        AiUmlReviewSnapshot snapshot=snapshots.build(command.projectId(),modelId,diagramId);
        AiProviderResult generated=provider.generate(prompts.build(source,snapshot));
        return new GeneratedAiReview(parser.parse(generated), snapshot.modelRevision(),
                snapshot.diagram()==null?null:snapshot.diagram().revision(), modelId, diagramId, source.getId());
    }
    private void validate(GenerateAiReviewCommand command,AiMessage source){boolean valid=source.getRole()==AiMessageRole.ASSISTANT&&source.getSkill()==AiSkill.UML_REVIEWER&&source.getProvider()!=null&&!source.getProvider().isBlank()&&source.getModel()!=null&&source.getSession()!=null&&source.getSession().getId().equals(command.sessionId())&&source.getSession().getProject().getId().equals(command.projectId());if(!valid)throw new AiReviewGenerationException("source must be an ASSISTANT UML_REVIEWER message in the requested project and session with model context");if(source.getDiagram()!=null&&!source.getDiagram().getModel().getId().equals(source.getModel().getId()))throw new AiReviewGenerationException("source diagram belongs to another model");}
}
