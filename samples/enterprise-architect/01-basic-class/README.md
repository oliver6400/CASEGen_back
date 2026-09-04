# EA Test 01 - Basic Class

## Objective

Identify how Enterprise Architect UML 2.5.1 XMI
represents a basic UML Class and its Properties.

## Model

Class: Customer

Attributes:
- id : String
- name : String
- email : String

Visibility:
- private

## Expected CASEGen import

UmlClass
- name = Customer

UmlProperty
- name = id
- type = String
- visibility = PRIVATE

UmlProperty
- name = name
- type = String
- visibility = PRIVATE

UmlProperty
- name = email
- type = String
- visibility = PRIVATE

## Findings

Enterprise Architect UML 2.5.1 exports:

- Package as `uml:Package`
- Class as `uml:Class`
- Attribute as `uml:Property`
- Primitive type through `PrimitiveTypes.xmi`
- Visibility using the `visibility` attribute
- Multiplicity using `lowerValue` and `upperValue`

The standard export does not contain
Enterprise Architect-specific `xmi:Extension` data.