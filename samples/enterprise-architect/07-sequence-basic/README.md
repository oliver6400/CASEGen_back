# EA Test 07 - Basic Sequence Diagram

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents a basic sequence interaction.

## Participants

- customer : Customer
- saleService : SaleService
- saleRepository : SaleRepository

## Messages

1. customer -> saleService : createSale()
2. saleService -> saleRepository : save()
3. saleRepository --> saleService : saveResult
4. saleService --> customer : createSaleResult

## Elements under investigation

- Interaction
- Lifeline / Instance
- Message
- Message ordering
- Synchronous Call
- Return Message
- Classifier reference
- Operation reference

# EA Test 07 - Basic Sequence Diagram

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents a basic sequence interaction and how
diagram layout is exported.

## Participants

- customer : Customer
- saleService : SaleService
- saleRepository : SaleRepository

## Messages

1. customer -> saleService : createSale()
2. saleService -> saleRepository : save()
3. saleRepository --> saleService : saveResult
4. saleService --> customer : createSaleResult

## Findings

Enterprise Architect represents the sequence model
using a UML Collaboration containing an UML Interaction.

Participants are represented as UML Lifelines.

Each Lifeline references a Property using the
`represents` attribute.

The represented Property references the participant
classifier through its UML type.

CASEGen can normalize this structure internally as:

Lifeline -> Classifier

Messages are represented using `uml:Message`.

Each message references:

- sendEvent
- receiveEvent

Both message ends are represented using
`uml:OccurrenceSpecification`.

An OccurrenceSpecification identifies its Lifeline
using the `covered` reference.

Messages corresponding to classifier operations can
reference an `uml:Operation` using the `signature`
attribute.

Return messages are exported with:

`messageSort="reply"`

Synchronous call messages can omit messageSort.

CASEGen must not infer chronological message order
from the physical XML element order.

Enterprise Architect extensions provide explicit
sequence numbers.

UMLDI diagram exports additionally preserve message
and Lifeline geometry through:

- UMLInteractionDiagram
- UMLShape
- UMLEdge
- bounds
- waypoint

CASEGen separates sequence semantics from diagram
layout.

Standard XMI is used primarily for semantic
interoperability.

EA diagram-enabled XMI can be used when diagram
layout and message positioning must also be preserved.