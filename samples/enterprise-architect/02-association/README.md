# EA Test 02 - Association and Multiplicity

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents a UML Association and the multiplicity
of its association ends.

## Model

Classes:

Customer
- id : String
- name : String

Sale
- id : String
- total : Real

Association:

Customer 1 -------- 0..* Sale

The association ends do not have role names and
their navigability is unspecified.

## Findings

Enterprise Architect exports the relationship using
`uml:Association`.

A binary association contains two ends represented
as UML Properties.

The association references its ends using
`memberEnd`.

Each end identifies its classifier using an XMI
reference.

Multiplicity is represented using:

- `lowerValue`
- `upperValue`

Examples:

1:
- lower = 1
- upper = 1

0..*:
- lower = 0
- upper = *

When navigability is unspecified, Enterprise Architect
can serialize the ends as `ownedEnd` of the association.

CASEGen must resolve association references using
`xmi:id` and `xmi:idref` and must not depend on the
physical order of XML elements.

CASEGen must represent UML multiplicity independently
from JPA relationship types such as OneToMany.