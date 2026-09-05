# EA Test 04 - Interface and Realization

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents a UML Interface and the realization of
that interface by a Class.

## Model

Interface:

Payable

Implementing Class:

PaymentService

Relationship:

PaymentService realizes Payable.

## Findings

Enterprise Architect exports Payable as:

`uml:Interface`

The interface is exported with:

`isAbstract = true`

PaymentService is exported as:

`uml:Class`

Enterprise Architect exports the implementation
relationship as:

`uml:Realization`

The relationship contains:

- client = PaymentService
- supplier = Payable

The Enterprise Architect extension additionally
represents:

- source = PaymentService
- target = Payable
- direction = Source -> Destination

CASEGen must rely primarily on the standard UML/XMI
client/supplier references.

CASEGen should normalize the relationship internally as
a UmlRealization.

When the supplier of a realization is an UmlInterface,
the Spring/Java generator can map the relation to:

`implements`