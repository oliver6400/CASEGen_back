import { test } from 'node:test'
import assert from 'node:assert/strict'
import { associationDecisions, identifiersFor, mappingDrafts } from '../src/features/modeling/lib/logicalPreparation.ts'

function fixture(upperA = 1, upperB = null) {
  return { id: 'version', versionNumber: 1, semanticSnapshot: {
    elements: [{ id: 'customer', name: 'Customer' }, { id: 'sale', name: 'Sale' }, { id: 'interface', name: 'Identifiable' },
      { id: 'customerId', name: 'ID' }, { id: 'saleId', name: 'id' }, { id: 'a', name: 'customer' }, { id: 'b', name: 'sales' }],
    classifiers: [{ element_id: 'customer', kind: 'CLASS' }, { element_id: 'sale', kind: 'CLASS' }, { element_id: 'interface', kind: 'INTERFACE' }],
    properties: [property('customerId', 'customer'), property('saleId', 'sale'),
      { ...property('a', null), primitive_type: null, type_classifier_id: 'customer', upper_bound: upperA },
      { ...property('b', null), primitive_type: null, type_classifier_id: 'sale', upper_bound: upperB }],
    associations: [{ element_id: 'association' }],
    associationEnds: [{ property_id: 'a', association_id: 'association', member_order: 1, aggregation: 'NONE', ownership: 'ASSOCIATION' },
      { property_id: 'b', association_id: 'association', member_order: 2, aggregation: 'NONE', ownership: 'ASSOCIATION' }], generalizations: [],
  } }
}
function property(id, owner) { return { element_id: id, owner_classifier_id: owner, primitive_type: 'STRING', lower_bound: 1, upper_bound: 1, is_static: false, is_derived: false } }
test('CLASS and INTERFACE get suggested targets; unique id is preselected ignoring case', () => {
  const drafts = mappingDrafts(fixture(), [])
  assert.deepEqual(drafts.map((d) => [d.target, d.idPropertyId]), [['ENTITY', 'customerId'], ['ENTITY', 'saleId'], ['JAVA_INTERFACE', '']])
})
test('identifier options never contain unrelated classifier properties', () => {
  assert.deepEqual(identifiersFor(fixture(), 'customer').map((p) => p.element_id), ['customerId'])
})
test('duplicate id names require a decision; explicit saved identifier is preserved', () => {
  const version = fixture(); version.semanticSnapshot.elements.push({ id: 'anotherId', name: 'id' }); version.semanticSnapshot.properties.push(property('anotherId', 'customer'))
  assert.equal(mappingDrafts(version, [])[0].idPropertyId, '')
  assert.equal(mappingDrafts(version, [{ umlElementId: 'customer', targetKind: 'ENTITY', configJson: { idPropertyId: 'anotherId' } }])[0].idPropertyId, 'anotherId')
})
test('JOINED identifiers may be inherited, but an owned unique id is preferred', () => {
  const version = fixture(); version.semanticSnapshot.generalizations.push({ specific_classifier_id: 'sale', general_classifier_id: 'customer' })
  assert.deepEqual(identifiersFor(version, 'sale').map((p) => p.element_id), ['customerId', 'saleId'])
  assert.equal(mappingDrafts(version, [])[1].idPropertyId, 'saleId')
})
test('1:N and N:1 resolve FK in many side regardless of other FKs', () => {
  const version = fixture(); const [d] = associationDecisions(version, mappingDrafts(version, []), {})
  assert.equal(d.ambiguous, false); assert.equal(d.automaticOwner, 'sale'); assert.match(d.description, /sale.customer_id/)
  const reversed = fixture(null, 1); const [r] = associationDecisions(reversed, mappingDrafts(reversed, []), {})
  assert.equal(r.automaticOwner, 'customer'); assert.match(r.description, /customer.sales_id/)
})
test('N:M resolves named join table automatically and honors advanced override', () => {
  const version = fixture(null, null); version.semanticSnapshot.elements.push({ id: 'association', name: 'Enrollment' })
  assert.match(associationDecisions(version, mappingDrafts(version, []), {})[0].description, /enrollment/)
  assert.match(associationDecisions(version, mappingDrafts(version, []), { associations: { association: { joinTableName: 'CustomJoin' } } })[0].description, /custom_join/)
})
test('composition resolves part → whole and cascade', () => {
  const version = fixture(); version.semanticSnapshot.associationEnds[0].aggregation = 'COMPOSITE'
  const [d] = associationDecisions(version, mappingDrafts(version, []), {})
  assert.equal(d.automaticOwner, 'sale'); assert.match(d.description, /CASCADE/); assert.equal(d.ambiguous, false)
})
test('ambiguous 1:1 asks for owner and saves a named decision without FK-count heuristic', () => {
  const version = fixture(1, 1); const drafts = mappingDrafts(version, [])
  assert.equal(associationDecisions(version, drafts, {})[0].ambiguous, true)
  const [d] = associationDecisions(version, drafts, { associations: { association: { fkOwnerClassifierId: 'customer' } } })
  assert.match(d.description, /customer.sales_id \+ UNIQUE/)
})
test('one classifier-owned end resolves dependent side exactly as V9', () => {
  const version = fixture(1, 1); version.semanticSnapshot.associationEnds[0].ownership = 'CLASSIFIER'; version.semanticSnapshot.properties.find((p) => p.element_id === 'a').owner_classifier_id = 'sale'
  const [d] = associationDecisions(version, mappingDrafts(version, []), {})
  assert.equal(d.ambiguous, false); assert.equal(d.automaticOwner, 'sale'); assert.match(d.description, /sale.customer_id/)
})
test('preparation does not mutate immutable version data', () => {
  const version = fixture(); const before = JSON.stringify(version)
  associationDecisions(version, mappingDrafts(version, []), {}); assert.equal(JSON.stringify(version), before)
})
test('selecting an older version uses only its captured classifiers, not editable model data', () => {
  const v2 = fixture();const v1 = structuredClone(v2);v1.semanticSnapshot.classifiers = v1.semanticSnapshot.classifiers.filter((c) => c.element_id === 'customer')
  assert.deepEqual(mappingDrafts(v1, []).map((d) => d.classifierId), ['customer'])
  assert.deepEqual(mappingDrafts(v2, []).map((d) => d.classifierId), ['customer', 'sale', 'interface'])
})
