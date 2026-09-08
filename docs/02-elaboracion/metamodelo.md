# Metamodelo

# Metamodelo CASEGen

## 1. Objetivo

Definir el metamodelo semántico utilizado internamente
por CASEGen para representar el subconjunto UML 2.5.1
seleccionado para el primer incremento.

El diseño se realizó a partir de fixtures creados
en Enterprise Architect y exportados mediante
XMI 2.5.1.

# Metamodelo
UmlElement
    │
    ▼
UmlNamedElement
    │
    ├── UmlPackage
    │
    └── UmlClassifier
            │
            ├── UmlClass
            └── UmlInterface


UmlClassifier
      │
      ├── UmlProperty [0..*]
      └── UmlOperation [0..*]

UmlOperation
      │
      └── UmlParameter [0..*]


UmlAssociation
      │
      └── UmlAssociationEnd [2]

UmlGeneralization
      ├── specific
      └── general

UmlRealization
      ├── client
      └── supplier


# Normalize
visibility ausente
→ PUBLIC

parameter direction ausente
→ IN

upper="*"
→ UNLIMITED

return type
→ UmlParameter direction=RETURN