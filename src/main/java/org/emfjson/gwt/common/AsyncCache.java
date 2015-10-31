/*
 * Copyright (c) 2015 Guillaume Hillairet.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Guillaume Hillairet - initial API and implementation
 *
 */
package org.emfjson.gwt.common;

import org.eclipse.emf.common.util.Callback;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncCache {

	public static final String EJS_JSON_ANNOTATION = "JSON";
	public static final String EJS_ELEMENT_ANNOTATION = "element";

	public final Map<EObject, URI> mapOfID = new HashMap<>();
	protected final Map<String, EClass> mapOfClasses = new HashMap<>();
	protected final Map<String, URI> mapOfURIs = new HashMap<>();

	private final Map<EClass, List<EReference>> mapOfReferences = new HashMap<>();
	private final Map<EClass, List<EReference>> mapOfContainments = new HashMap<>();
	private final Map<EClass, List<EAttribute>> mapOfAttributes = new HashMap<>();

	private final Map<EStructuralFeature, String> mapOfNames = new HashMap<>();
	private final Map<EClass, Map<String, EStructuralFeature>> mapOfFeatures = new HashMap<>();
	private final Map<EClass, String> mapOfTypes = new HashMap<>();

	public void getEClass(final ResourceSet resourceSet, final String type, final Callback<EClass> callback) {
		if (type == null) {
			callback.onFailure(new IllegalArgumentException("Cannot get EClass from null identifier."));
		}

		final EClass eClass = mapOfClasses.get(type);
		if (eClass == null) {
			URI uri = mapOfURIs.get(type);
			if (uri == null) {
				uri = URI.createURI(type);
				mapOfURIs.put(type, uri);
			}
			resourceSet.getEObject(uri, new Callback<EObject>() {
				@Override
				public void onSuccess(EObject result) {
					if (result instanceof EClass) {
						final EClass resultEClass = (EClass) result;
						mapOfClasses.put(type, resultEClass);
						callback.onSuccess(resultEClass);
					} else {
						callback.onFailure(new ClassCastException("Cannot cast object " + result + " as EClass."));
					}
				}

				@Override
				public void onFailure(Throwable caught) {
					callback.onFailure(caught);
				}
			});
		} else {
			callback.onSuccess(eClass);
		}
	}

	public static String getElementName(EStructuralFeature feature) {
		final EAnnotation annotation = feature.getEAnnotation(EJS_JSON_ANNOTATION);
		if (annotation != null && annotation.getDetails().containsKey(EJS_ELEMENT_ANNOTATION)) {
			return annotation.getDetails().get(EJS_ELEMENT_ANNOTATION);
		}
		return feature.getName();
	}

	/**
	 * Returns the field name to be used by a structural feature.
	 *
	 * Custom names can be defined by using an eAnnotation on a eStructuralFeature with
	 * a source named JSON and a value being the field name.
	 *
	 * @param feature
	 * @return field name
	 */
	public String getKey(EStructuralFeature feature) {
		String key = mapOfNames.get(feature);
		if (key == null) {
			key = getElementName(feature);
			mapOfNames.put(feature, key);
		}
		return key;
	}

	/**
	 * Returns all references (non containments) of a eClass.
	 * First checks that the references are present in the cache, if
	 * not first cache it, then return the list of references.
	 *
	 * @param eClass
	 * @return list of references
	 */
	public List<EReference> getReferences(EClass eClass) {
		List<EReference> references = mapOfReferences.get(eClass);
		if (references == null) {
			references = new ArrayList<>();
			for (EReference reference : eClass.getEAllReferences()) {
				if (!reference.isContainment()) {
					references.add(reference);
				}
			}

			mapOfReferences.put(eClass, references);
		}
		return references;
	}

	/**
	 * Returns all containments of a eClass.
	 * First checks that the containments are present in the cache, if
	 * not first cache it, then return the list of containments.
	 *
	 * @param eClass
	 * @return list of containments
	 */
	public List<EReference> getContainments(EClass eClass) {
		List<EReference> references = mapOfContainments.get(eClass);
		if (references == null) {
			references = eClass.getEAllContainments();
			mapOfContainments.put(eClass, references);
		}
		return references;
	}

	/**
	 * Returns all attributes of a eClass.
	 * First checks that the attributes are present in the cache, if
	 * not first cache it, then return the list of attributes.
	 * @param eClass
	 * @return list of attributes
	 */
	public List<EAttribute> getAttributes(EClass eClass) {
		List<EAttribute> attributes = mapOfAttributes.get(eClass);
		if (attributes == null) {
			attributes = eClass.getEAllAttributes();
			mapOfAttributes.put(eClass, attributes);
		}
		return attributes;
	}

	/**
	 * Returns a eClass by it's full URI.
	 *
	 * If the eClass has not yet been cached, the method will cache it's
	 * URI object created from the string parameter, retrieve the eClass
	 * and cache it.
	 *
	 * @param resourceSet
	 * @param uri
	 * @return eClass
	 */
	public EClass getEClass(ResourceSet resourceSet, String uri) {
		EClass eClass = mapOfClasses.get(uri);
		if (eClass == null) {
			URI realURI = mapOfURIs.get(uri);
			if (realURI == null) {
				realURI = URI.createURI(uri);
				mapOfURIs.put(uri, realURI);
			}
			eClass = (EClass) resourceSet.getEObject(realURI, true);
			if (eClass != null) {
				mapOfClasses.put(uri, eClass);
			}
		}
		return eClass;
	}

	/**
	 * Returns the eStructuralFeature of a given eClass from a key. The key
	 * corresponds to a field name.
	 *
	 * @param eClass
	 * @param key
	 * @return eStructuralFeature
	 */
	public EStructuralFeature getEStructuralFeature(EClass eClass, String key) {
		Map<String, EStructuralFeature> featureByKey = mapOfFeatures.get(eClass);

		if (featureByKey == null) {
			featureByKey = new HashMap<>();
			mapOfFeatures.put(eClass, featureByKey);
		}

		EStructuralFeature feature = featureByKey.get(key);
		if (feature == null) {
			feature = findEStructuralFeature(eClass, key);
			if (feature != null) {
				featureByKey.put(key, feature);
			}
		}

		return feature;
	}

	private EStructuralFeature findEStructuralFeature(EClass eClass, String key) {
		if (eClass == null || key == null)
			return null;

		EStructuralFeature eStructuralFeature = eClass.getEStructuralFeature(key);

		if (eStructuralFeature == null) {
			int i = 0;
			List<EStructuralFeature> features = eClass.getEAllStructuralFeatures();
			while (i < features.size() && eStructuralFeature == null) {
				EStructuralFeature current = features.get(i);
				if (key.equals(getKey(current))) {
					eStructuralFeature = current;
				}
				i++;
			}
		}

		return eStructuralFeature;
	}

	/**
	 * Returns the uri of a eClass.
	 *
	 * @param eClass
	 * @return uri
	 */
	public String getType(EClass eClass) {
		if (eClass == null) {
			return null;
		}

		if (mapOfTypes.containsKey(eClass)) {
			return mapOfTypes.get(eClass);
		}

		final URI uri = EcoreUtil.getURI(eClass);
		mapOfTypes.put(eClass, uri.toString());

		return uri.toString();
	}

	public URI getURI(EObject object) {
		if (object == null) {
			return null;
		}

		if (mapOfID.containsKey(object)) {
			return mapOfID.get(object);
		}

		URI uri = ((InternalEObject) object).eProxyURI();
		if (uri == null) {
			uri = EcoreUtil.getURI(object);
		}

		mapOfID.put(object, uri);

		return uri;
	}

}
