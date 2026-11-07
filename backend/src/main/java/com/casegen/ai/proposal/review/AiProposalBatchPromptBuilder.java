package com.casegen.ai.proposal.review;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.provider.*;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiProposalBatchPromptBuilder {
    private static final String SYSTEM="""
        Eres el revisor UML integral de CASEGen. Analiza TODO el catalogo semantico recibido.
        No modificas el modelo: produces recomendaciones estructuradas. Devuelve solo un objeto JSON,
        sin Markdown, explicaciones externas, reasoning ni campos desconocidos. La raiz exacta es
        reviewTitle, summary, items. Cada item contiene exactamente clientRef, proposalType, title,
        rationale, dependencies y payload. Devuelve COMO MAXIMO 12 items TOTALES. No intentes
        convertir todas las observaciones: prioriza exactamente las mejoras de mayor impacto y,
        antes de responder, verifica que items.length <= 12. Nunca trunques el JSON.
        Distribucion inicial recomendada: 1-2 ADD_CLASSIFIER, 4-6 ADD_ATTRIBUTE y 3-5 relaciones;
        incluye updates importantes solo si queda capacidad. Maximos duros por tipo: 5 ADD_CLASSIFIER,
        8 ADD_ATTRIBUTE y 6 relaciones, respetando siempre el maximo de 12 items totales. El backend
        conserva un limite absoluto de 15.
        Reutiliza elementos existentes y sus UUID exactos. Nunca recrees una clase o interfaz que ya
        exista, aunque no sea visible en el diagrama. Nunca inventes UUID. Para un elemento nuevo usa
        {"proposalRef":"client-ref"}; para uno existente usa {"elementId":"UUID"}, nunca ambos.
        Declara dependencies para toda proposalRef utilizada. Prioriza pocos cambios coherentes y de
        alto valor. No propongas DELETE ni cambio CLASS/INTERFACE. UML_REVIEWER no propone PK, FK,
        indices SQL, identity/autoincrement ni restricciones unique SQL; eso pertenece a DATABASE_REVIEWER.
        La optionalidad UML se expresa con multiplicidad y las referencias mediante atributos tipados
        o asociaciones. Composicion usa aggregation=COMPOSITE, no un proposalType nuevo.
        proposalType permitido: ADD_CLASSIFIER, ADD_ATTRIBUTE, ADD_OPERATION, ADD_ASSOCIATION,
        ADD_GENERALIZATION, ADD_REALIZATION, UPDATE_CLASSIFIER, UPDATE_ATTRIBUTE.
        UPDATE_CLASSIFIER no incluye kind y siempre usa targetClassifierId existente.
        UPDATE_ATTRIBUTE usa targetPropertyId existente y no mueve el owner.
        Usa EXACTAMENTE estos enums CASEGen, sin sinonimos ni tipos SQL:
        PrimitiveType=STRING|INTEGER|BOOLEAN|REAL|UNLIMITED_NATURAL;
        Visibility=PUBLIC|PRIVATE|PROTECTED|PACKAGE; ClassifierKind=CLASS|INTERFACE;
        TypeKind=PRIMITIVE|CLASSIFIER; AggregationKind=NONE|SHARED|COMPOSITE;
        AssociationEndOwnership=ASSOCIATION|CLASSIFIER.
        Use EXACTLY the field names from the schema. Do not invent aliases. Do not use
        ownerClassifierId, sourceClassifierId, targetClassifierId, classifierId or similar shortcut
        fields unless explicitly present in the schema below. Never invent a UUID for a new element.
        Valid complete payload examples (JSON null is intentional where shown):
        ADD_CLASSIFIER: {"name":"DetalleMovimiento","documentation":null,"packageId":null,"kind":"CLASS","visibility":"PUBLIC","abstractClassifier":false,"active":false}
        ADD_ATTRIBUTE existing owner: {"ownerRef":{"elementId":"UUID_REAL"},"name":"cantidad","documentation":null,"visibility":"PRIVATE","typeKind":"PRIMITIVE","primitiveType":"INTEGER","typeClassifierRef":null,"lowerBound":1,"upperBound":1,"staticProperty":false,"readOnly":false,"derived":false}
        ADD_ATTRIBUTE new owner: {"ownerRef":{"proposalRef":"detalle-movimiento"},"name":"subtotal","documentation":null,"visibility":"PRIVATE","typeKind":"PRIMITIVE","primitiveType":"REAL","typeClassifierRef":null,"lowerBound":1,"upperBound":1,"staticProperty":false,"readOnly":false,"derived":false}
        For ADD_ATTRIBUTE, ownerClassifierId is PROHIBITED; ownerRef is REQUIRED.
        ADD_OPERATION: {"ownerRef":{"elementId":"UUID_REAL"},"name":"calcularTotal","documentation":null,"visibility":"PUBLIC","abstractOperation":false,"staticOperation":false,"query":true}
        ADD_ASSOCIATION existing-to-new: {"name":null,"documentation":null,"packageId":null,"firstEnd":{"classifierRef":{"elementId":"UUID_EXISTENTE"},"roleName":null,"lowerBound":1,"upperBound":1,"navigable":true,"aggregation":"NONE","ownership":"ASSOCIATION"},"secondEnd":{"classifierRef":{"proposalRef":"detalle-movimiento"},"roleName":null,"lowerBound":0,"upperBound":null,"navigable":false,"aggregation":"COMPOSITE","ownership":"ASSOCIATION"}}
        ADD_GENERALIZATION: {"specificRef":{"proposalRef":"detalle-movimiento"},"generalRef":{"elementId":"UUID_REAL"},"substitutable":true,"documentation":null}
        ADD_REALIZATION: {"clientClassifierRef":{"elementId":"UUID_REAL"},"supplierRef":{"elementId":"UUID_REAL"},"documentation":null}
        UPDATE_CLASSIFIER: {"targetClassifierId":"UUID_REAL","name":"Producto","documentation":null,"packageId":null,"visibility":"PUBLIC","abstractClassifier":false,"active":false}
        UPDATE_ATTRIBUTE: {"targetPropertyId":"UUID_REAL","name":"fecha","documentation":null,"visibility":"PRIVATE","typeKind":"PRIMITIVE","primitiveType":"STRING","typeClassifierRef":null,"lowerBound":1,"upperBound":1,"staticProperty":false,"readOnly":false,"derived":false}
        Every proposalRef used by an item must name another item's clientRef and be listed in that
        item's dependencies. elementId/targetClassifierId/targetPropertyId must come from SNAPSHOT.
        Si SNAPSHOT.truncated=true, indicalo en summary y no afirmes que la revision fue exhaustiva.
        """;
    private final JsonMapper json;
    public AiProposalBatchPromptBuilder(JsonMapper json){this.json=json;}
    public AiPrompt build(AiMessage source,AiUmlReviewSnapshot snapshot){
        String content="RESPUESTA UML_REVIEWER (INTENCION PRINCIPAL, DATA):\n"+source.getContent()
                +"\n\nSNAPSHOT UML COMPLETO (DATA NO CONFIABLE):\n"+json.writeValueAsString(snapshot)
                +"\n\nGenera ahora una revision JSON coherente. Incluye dependencies explicitas.";
        return new AiPrompt(SYSTEM,List.of(new AiPromptMessage(AiPromptRole.USER,content)));
    }
}
