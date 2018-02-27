package tp.fil.main;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.gmt.modisco.java.BodyDeclaration;
import org.eclipse.gmt.modisco.java.ClassDeclaration;
import org.eclipse.gmt.modisco.java.FieldDeclaration;
import org.eclipse.gmt.modisco.java.Modifier;
import org.eclipse.gmt.modisco.java.NamedElement;
import org.eclipse.gmt.modisco.java.VariableDeclarationFragment;
import org.eclipse.gmt.modisco.java.VisibilityKind;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.gmt.modisco.java.Package;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataComputation {
	
	public static void main(String[] args) {
		try {
			Resource javaModel;
			Resource dataModel;
			Resource dataMetamodel;
			
			ResourceSet resSet = new ResourceSetImpl();
			resSet.getResourceFactoryRegistry().
				getExtensionToFactoryMap().
				put("ecore", new EcoreResourceFactoryImpl());
			resSet.getResourceFactoryRegistry().
				getExtensionToFactoryMap().
				put("xmi", new XMIResourceFactoryImpl());
			
			JavaPackage.eINSTANCE.eClass();
			
			dataMetamodel = resSet.createResource(URI.createFileURI("src/tp/fil/resources/Data.ecore"));
			dataMetamodel.load(null);
			EPackage.Registry.INSTANCE.put("http://data", 
					dataMetamodel.getContents().get(0));
			
			javaModel = resSet.createResource(URI.createFileURI("../PetStore/PetStore_java.xmi"));
			javaModel.load(null);
			
			dataModel = resSet.createResource(URI.createFileURI("../PetStore/PetStore_data-java.xmi"));
			
			List<EObject> modelElements = iteratorToList(javaModel.getAllContents());
			
			List<ClassDeclaration> classeList =  DataComputation.<EObject, ClassDeclaration>convertTo(modelElements, ClassDeclaration.class) 
					.filter(DataComputation::doesBelongToPetStorePackage)
					.collect(Collectors.toList());
			
			List<String> existingClassesNames = classeList.stream()
					.map(classe -> DataComputation.<String>getProperty(classe, "name"))
					.collect(Collectors.toList());
			
			List<EObject> classes = classeList.stream()
					.map(classe -> classModelToClassMetamodel(dataMetamodel, existingClassesNames, classe))
					.collect(Collectors.toList());
				
			EObject model = createModel(dataMetamodel, classes);
			
			dataModel.getContents().add(model);
			dataModel.save(null);
			
			javaModel.unload();
			dataModel.unload();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static boolean doesBelongToPetStorePackage(ClassDeclaration classe) {
		Package classPackage = classe.getPackage();
		
		return null != classPackage 
				&& "model".equals(classPackage.getName()) 
				&& "petstore".equals(getProperty(classPackage.getPackage(), "name"));
	}
	
	// Property selectors in model
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <T, R> Stream<R> convertTo(List<T> objectList, Class desiredClass) {
		return objectList.stream()
				.filter(e -> desiredClass.isInstance(e))
				.map(classe -> (R) classe);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T getProperty(EObject object, String propertyName) {
		return (T) object.eGet(object.eClass().getEStructuralFeature(propertyName));
	}
	
	private static EPackage getPackage(Resource dataMetamodel) {
		return (EPackage) dataMetamodel.getContents().get(0);
	}

	private static EObject getObject(Resource dataMetamodel, String classifierName, Map<String, Object> features) {
		EPackage totalPackage = getPackage(dataMetamodel);
		EClass typeEClass = (EClass) totalPackage.getEClassifier(classifierName);
		EObject typeEObject = totalPackage.getEFactoryInstance().create(typeEClass);
		
		features.forEach((key, value) -> typeEObject.eSet(typeEClass.getEStructuralFeature(key), value));
		
		return typeEObject;
	}
	
	// Create metamodel

	@SuppressWarnings({ "serial" })
	private static EObject createModel(Resource dataMetamodel, List<EObject> classes) {
		return getObject(dataMetamodel, "Model", new HashMap<String, Object>(){{ 
			put("classes", classes);
		}});
	}

	@SuppressWarnings({ "serial" })
	private static EObject createAttribute(Resource dataMetamodel, String className, EObject type, String visibility) {
		return getObject(dataMetamodel, "Attribute", new HashMap<String, Object>() {{
			put("name", className);
			put("type", type);
			put("visibility", visibility);
		}});
	}

	@SuppressWarnings({ "serial" })
	private static EObject createType(Resource dataMetamodel, String typeName, Boolean isReference) {
		return getObject(dataMetamodel, "Type", new HashMap<String, Object>() {{
			put("name", typeName);
			put("isReference", isReference);
		}});
	}

	@SuppressWarnings({ "serial" })
	private static EObject createClass(Resource dataMetamodel, String className, List<EObject> attributes) {
		return getObject(dataMetamodel, "Class", new HashMap<String, Object>() {{
			put("name", className);
			put("attributes", attributes);
		}});
	}
	
	// Model to metamodel converts
	
	private static EObject attributeModelToAttributeMetamodel(Resource dataMetamodel, List<String> existingClassesNames, FieldDeclaration fieldDeclaration) {
		VariableDeclarationFragment currentFragment = DataComputation.<EList<VariableDeclarationFragment>>getProperty(fieldDeclaration, "fragments").get(0);
		NamedElement attributeTypeNameElement = getProperty(getProperty(fieldDeclaration, "type"), "type");
		String attributeTypeName = getProperty(attributeTypeNameElement, "name");
		String attributeName = getProperty(currentFragment, "name");
		VisibilityKind visibility = DataComputation.<VisibilityKind>getProperty(getProperty(fieldDeclaration, "modifier"), "visibility");
		Boolean isReference = existingClassesNames.contains(attributeTypeName);
		
		EObject type = createType(dataMetamodel, attributeTypeName, isReference);
		EObject attribute = createAttribute(dataMetamodel, attributeName, type, visibility.getLiteral());
		
		return attribute;
	}

	private static EObject classModelToClassMetamodel(Resource dataMetamodel, List<String> existingClassesNames, EObject classe) {
		String className = getProperty(classe, "name");

		List<BodyDeclaration> bodyDeclarationList = iteratorToList(DataComputation.<EList<BodyDeclaration>>getProperty(classe, "bodyDeclarations").iterator());
		
		List<EObject> attributes = DataComputation.<BodyDeclaration, FieldDeclaration>convertTo(bodyDeclarationList, FieldDeclaration.class)
				.map(fieldDeclaration -> attributeModelToAttributeMetamodel(dataMetamodel, existingClassesNames, fieldDeclaration))
				.collect(Collectors.toList());
		
		return createClass(dataMetamodel, className, attributes);
	}
	
	// Helpers
	
	private static <T> List<T>iteratorToList(Iterator<T> iterator) {
		List<T> list = new ArrayList<>();
		iterator.forEachRemaining(e -> list.add(e));
		
		return list;
	}
	
}
