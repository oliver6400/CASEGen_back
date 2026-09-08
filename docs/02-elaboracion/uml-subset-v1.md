# CASEGen UML 2.5.1 Subset v1

## Estado

FROZEN FOR MVP

## Elementos soportados

### Model organization
- Model
- Package

### Classifiers
- Class
- Interface

### Features
- Property
- Operation
- Parameter

### Relationships
- Association
- Generalization
- Realization

### Supporting concepts
- Association End
- Multiplicity
- Visibility
- Primitive Type
- Parameter Direction

## Primitive Types

- String
- Integer
- Boolean
- Real
- UnlimitedNatural

## Visibility

- public
- private
- protected
- package

## Parameter Direction

- in
- out
- inout
- return

## Scope

This subset defines the structural UML model supported
by CASEGen MVP.

Additional UML diagram types and metaclasses may be
incorporated in later iterations without changing the
core interoperability strategy.

## Sequence Diagram

Supported semantic elements:

- Interaction
- Lifeline
- Message
- OccurrenceSpecification

Supported message types:

- Synchronous Call
- Reply

Supported references:

- Lifeline -> Classifier
- Message -> Operation
- Message -> Send Occurrence
- Message -> Receive Occurrence

Not included in MVP v1:

- CombinedFragment
- alt
- loop
- opt
- parallel fragments
- state invariant
- gates
- asynchronous signals
- creation/destruction semantics

## Sequence Diagram

Supported semantic elements:

- Interaction
- Lifeline
- Message
- OccurrenceSpecification

Supported message types:

- Synchronous Call
- Reply

Supported references:

- Lifeline -> Classifier
- Message -> Operation
- Message -> Send Occurrence
- Message -> Receive Occurrence

Not included in MVP v1:

- CombinedFragment
- alt
- loop
- opt
- parallel fragments
- state invariant
- gates
- asynchronous signals
- creation/destruction semantics