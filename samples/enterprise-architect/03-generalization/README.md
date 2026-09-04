# EA Test 03 - Generalization

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents class inheritance/generalization.

## Model

Person
- id : String
- name : String

Customer extends Person
- email : String

## Expected CASEGen representation

UmlGeneralization

- general = Person
- specific = Customer

## Expected Java mapping

class Customer extends Person

## Findings

Enterprise Architect UML 2.5.1 represents inheritance
using uml:Generalization.

The generalization is owned by the specific classifier.

For:

Customer extends Person

Customer is the specific classifier and Person is the
general classifier.

The parent classifier is referenced using the
`general` attribute and its XMI identifier.

The Enterprise Architect extension additionally
represents the connector as:

source = Customer
target = Person

CASEGen must resolve `general` through xmi:id and must
not depend on the physical ordering of elements in XMI.

CASEGen representation:

UmlGeneralization
- specific = Customer
- general = Person
- isSubstitutable = true