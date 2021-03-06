package org.geneontology.minerva.server.handler;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonTools;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * Methods for creating a new model. This handles also all the default
 * annotations for models and provides methods to update date annotations
 */
abstract class ModelCreator {
	
	final UndoAwareMolecularModelManager m3;
	final CurieHandler curieHandler;
	private final String defaultModelState;
	private final Set<IRI> dataPropertyIRIs;

	static interface VariableResolver {
		public boolean notVariable(String id);
		public OWLNamedIndividual getVariableValue(String id) throws UnknownIdentifierException;
		
		static final VariableResolver EMPTY = new VariableResolver() {
			
			@Override
			public boolean notVariable(String id) {
				return true;
			}
			
			@Override
			public OWLNamedIndividual getVariableValue(String id) {
				return null;
			}
		};
	}
	
	ModelCreator(UndoAwareMolecularModelManager models, String defaultModelState) {
		this.m3 = models;
		this.curieHandler = models.getCuriHandler();
		this.defaultModelState = defaultModelState;
		Set<IRI> dataPropertyIRIs = new HashSet<IRI>();
		for(OWLDataProperty p : m3.getOntology().getDataPropertiesInSignature(true)) {
			dataPropertyIRIs.add(p.getIRI());
		}
		this.dataPropertyIRIs = Collections.unmodifiableSet(dataPropertyIRIs);
	}

	ModelContainer createModel(String userId, UndoMetadata token, VariableResolver resolver, JsonAnnotation[] annotationValues) throws UnknownIdentifierException, OWLOntologyCreationException {
		ModelContainer model = m3.generateBlankModel(token);
		Set<OWLAnnotation> annotations = extract(annotationValues, userId, resolver, model);
		annotations = addDefaultModelState(annotations, model.getOWLDataFactory());
		if (annotations != null) {
			m3.addModelAnnotations(model, annotations, token);
		}
		updateModelAnnotations(model, userId, token, m3);
		return model;
	}
	
	boolean deleteModel(ModelContainer model) {
		if (model != null) {
			return m3.deleteModel(model);
		}
		return false;
	}
	
	private Set<OWLAnnotation> addDefaultModelState(Set<OWLAnnotation> existing, OWLDataFactory f) {
		IRI iri = AnnotationShorthand.modelstate.getAnnotationProperty();
		OWLAnnotationProperty property = f.getOWLAnnotationProperty(iri);
		OWLAnnotation ann = f.getOWLAnnotation(property, f.getOWLLiteral(defaultModelState));
		if (existing == null || existing.isEmpty()) {
			return Collections.singleton(ann);
		}
		existing.add(ann);
		return existing;
	}
	
	Set<OWLAnnotation> extract(JsonAnnotation[] values, String userId, VariableResolver batchValues, ModelContainer model) throws UnknownIdentifierException {
		Set<OWLAnnotation> result = new HashSet<OWLAnnotation>();
		OWLDataFactory f = model.getOWLDataFactory();
		if (values != null) {
			for (JsonAnnotation jsonAnn : values) {
				if (jsonAnn.key != null && jsonAnn.value != null) {
					AnnotationShorthand shorthand = AnnotationShorthand.getShorthand(jsonAnn.key, curieHandler);
					if (shorthand != null) {
						if (AnnotationShorthand.evidence == shorthand) {
							IRI evidenceIRI;
							if (batchValues.notVariable(jsonAnn.value)) {
								evidenceIRI = curieHandler.getIRI(jsonAnn.value);
							}
							else {
								evidenceIRI = batchValues.getVariableValue(jsonAnn.value).getIRI();
							}
							result.add(create(f, shorthand, evidenceIRI));
						}
						else {
							result.add(create(f, shorthand, JsonTools.createAnnotationValue(jsonAnn, f)));
						}
					}
					else {
						IRI pIRI = curieHandler.getIRI(jsonAnn.key);
						if (dataPropertyIRIs.contains(pIRI) == false) {
							OWLAnnotationValue annotationValue = JsonTools.createAnnotationValue(jsonAnn, f);
							result.add(f.getOWLAnnotation(f.getOWLAnnotationProperty(pIRI), annotationValue));
						}
					}
				}
			}
		}
		addGeneratedAnnotations(userId, result, f);
		return result;
	}
	
	Map<OWLDataProperty, Set<OWLLiteral>> extractDataProperties(JsonAnnotation[] values, ModelContainer model) {
		Map<OWLDataProperty, Set<OWLLiteral>> result = new HashMap<OWLDataProperty, Set<OWLLiteral>>();
		
		if (values != null && values.length > 0) {
			OWLDataFactory f = model.getOWLDataFactory();
			for (JsonAnnotation jsonAnn : values) {
				if (jsonAnn.key != null && jsonAnn.value != null) {
					AnnotationShorthand shorthand = AnnotationShorthand.getShorthand(jsonAnn.key, curieHandler);
					if (shorthand == null) {
						IRI pIRI = curieHandler.getIRI(jsonAnn.key);
						if (dataPropertyIRIs.contains(pIRI)) {
							OWLLiteral literal = JsonTools.createLiteral(jsonAnn, f);
							if (literal != null) {
								OWLDataProperty property = f.getOWLDataProperty(pIRI);
								Set<OWLLiteral> literals = result.get(property);
								if (literals == null) {
									literals = new HashSet<OWLLiteral>();
									result.put(property, literals);
								}
								literals.add(literal);
							}
						}
					}
				}
			}
		}
		
		return result;
	}
	
	void updateDate(ModelContainer model, OWLNamedIndividual individual, UndoMetadata token, UndoAwareMolecularModelManager m3) throws UnknownIdentifierException {
		final OWLDataFactory f = model.getOWLDataFactory();
		m3.updateAnnotation(model, individual, createDateAnnotation(f), token);
	}
	
	void updateModelAnnotations(ModelContainer model, String userId, UndoMetadata token, MolecularModelManager<UndoMetadata> m3) throws UnknownIdentifierException {
		final OWLDataFactory f = model.getOWLDataFactory();
		if (userId != null) {
			Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
			annotations.add(create(f, AnnotationShorthand.contributor, userId));
			m3.addModelAnnotations(model, annotations, token);
		}
		m3.updateAnnotation(model, createDateAnnotation(f), token);
	}
	
	void addGeneratedAnnotations(String userId, Set<OWLAnnotation> annotations, OWLDataFactory f) {
		if (userId != null) {
			annotations.add(create(f, AnnotationShorthand.contributor, userId));
		}
	}
	
	void addDateAnnotation(Set<OWLAnnotation> annotations, OWLDataFactory f) {
		annotations.add(createDateAnnotation(f));
	}
	
	OWLAnnotation createDateAnnotation(OWLDataFactory f) {
		return create(f, AnnotationShorthand.date, generateDateString());
	}
	
	/**
	 * separate method, intended to be overridden during test.
	 * 
	 * @return date string, never null
	 */
	protected String generateDateString() {
		String dateString = MolecularModelJsonRenderer.AnnotationTypeDateFormat.get().format(new Date());
		return dateString;
	}
	
	Set<OWLAnnotation> createGeneratedAnnotations(ModelContainer model, String userId) {
		Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
		OWLDataFactory f = model.getOWLDataFactory();
		addGeneratedAnnotations(userId, annotations, f);
		return annotations;
	}
	
	void updateDate(ModelContainer model, OWLObjectProperty predicate, OWLNamedIndividual subject, OWLNamedIndividual object, UndoMetadata token, UndoAwareMolecularModelManager m3) throws UnknownIdentifierException {
		final OWLDataFactory f = model.getOWLDataFactory();
		m3.updateAnnotation(model, predicate, subject, object, createDateAnnotation(f), token);
	}
	
	static OWLAnnotation create(OWLDataFactory f, AnnotationShorthand s, String literal) {
		return create(f, s, f.getOWLLiteral(literal));
	}
	
	static OWLAnnotation create(OWLDataFactory f, AnnotationShorthand s, OWLAnnotationValue v) {
		final OWLAnnotationProperty p = f.getOWLAnnotationProperty(s.getAnnotationProperty());
		return f.getOWLAnnotation(p, v);
	}
}
