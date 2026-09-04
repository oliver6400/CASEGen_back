# EA Test 02B - Association Roles and Navigability

## Objective

Determine how Enterprise Architect UML 2.5.1
represents association-end names, visibility,
multiplicity and navigability.

## Model

Customer 1 <-> 0..* Sale

Navigable roles:

Customer:
- sales : Sale[0..*]

Sale:
- customer : Customer[1]

Both association ends are private and navigable.

## Findings

Enterprise Architect exports a navigable association
end as an uml:Property owned by the corresponding
uml:Class.

The uml:Association references both properties using
memberEnd.

The UML standard XMI preserves:

- role name
- property type
- visibility
- lower multiplicity
- upper multiplicity
- association reference

Enterprise Architect extensions additionally expose:

- isNavigable
- direction = Bi-Directional
- role metadata

CASEGen must resolve relationships through
xmi:id and xmi:idref instead of relying on XML order.