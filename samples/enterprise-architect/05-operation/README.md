# EA Test 05 - Operation and Parameters

## Objective

Determine how Enterprise Architect UML 2.5.1 XMI
represents operations, input parameters and return
parameters.

## Model

Customer

Attribute:
- id : String

Operation:
+ register(email : String) : Boolean

## Findings

Enterprise Architect exports an UML operation using:

`ownedOperation`

Input parameters are exported using:

`ownedParameter`

The email parameter references the UML primitive
type String.

When the input parameter direction is `in`,
Enterprise Architect can omit the `direction`
attribute because `in` is treated as the default.

The return type is not represented as a direct
attribute of the operation.

Instead, Enterprise Architect creates an
`ownedParameter` with:

`direction = return`

and the corresponding UML type.

For this fixture:

- email : String, direction = IN
- return : Boolean, direction = RETURN

Public operation visibility can be omitted in
standard XMI and must therefore be interpreted as
the UML default.

CASEGen must normalize omitted default values during
XMI import.

## Expected CASEGen representation

UmlOperation:
- name = register
- visibility = PUBLIC

Parameters:

1.
- name = email
- type = String
- direction = IN

2.
- name = return
- type = Boolean
- direction = RETURN

## Expected Java mapping

public Boolean register(String email)