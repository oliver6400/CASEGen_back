# EA Test 06 - Integrated Domain Model

## Objective

Validate that the CASEGen UML 2.5.1 subset can coexist
in a single integrated domain model.

## Elements

- UML Interface
- UML Classes
- Properties
- Operations
- Parameters
- Generalization
- Realization
- Associations
- Association roles
- Multiplicities
- Navigability
- Primitive Types

## Model

Identifiable
- getId() : String

Person
- id : String
- name : String
- validate() : Boolean

Customer extends Person
- email : String
- register(email : String) : Boolean

Sale
- id : String
- total : Real

SaleDetail
- id : String
- quantity : Integer
- subtotal : Real

Product
- id : String
- name : String
- price : Real

## Relationships

Person realizes Identifiable.

Customer generalizes to Person.

Customer 1 <-> 0..* Sale

Roles:
- Customer.sales
- Sale.customer

Sale 1 <-> 1..* SaleDetail

Roles:
- Sale.details
- SaleDetail.sale

Product 1 <-> 0..* SaleDetail

Roles:
- Product.details
- SaleDetail.product

## Findings

The integrated UML 2.5.1 model successfully combines:

- Class
- Interface
- Property
- Operation
- Parameter
- Generalization
- Realization
- Association
- Association-end roles
- Multiplicity
- Navigability
- UML Primitive Types

Enterprise Architect represents:

- classes using `uml:Class`
- interfaces using `uml:Interface`
- attributes and navigable association ends using `uml:Property`
- operations using `ownedOperation`
- parameters and return values using `ownedParameter`
- inheritance using `uml:Generalization`
- implementation relationships using `uml:Realization`
- binary relationships using `uml:Association`
- association references using `memberEnd`

CASEGen must resolve all cross references through
XMI identifiers rather than XML element order.

The standard XMI representation is the primary
interchange source.

Enterprise Architect extensions are considered
optional metadata and must not be required to
reconstruct the semantic UML model.