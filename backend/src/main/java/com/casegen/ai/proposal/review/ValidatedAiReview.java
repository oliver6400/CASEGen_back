package com.casegen.ai.proposal.review;
import com.casegen.ai.proposal.domain.AiProposalType;
import java.util.*;
import tools.jackson.databind.JsonNode;
public record ValidatedAiReview(String title,String summary,long baseModelRevision,Long baseDiagramRevision,
 String provider,UUID modelId,UUID diagramId,UUID sourceMessageId,List<Item> items){public ValidatedAiReview{items=List.copyOf(items);}
 public record Item(String clientRef,AiProposalType proposalType,String title,String rationale,
  List<String> dependencies,JsonNode canonicalPayload,int batchOrder){public Item{dependencies=List.copyOf(dependencies);}}}
