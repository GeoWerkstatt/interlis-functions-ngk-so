package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.logging.EhiLogger;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.ObjectPath;
import ch.interlis.ili2c.metamodel.PathEl;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.validator.Validator;
import ch.interlis.iox_j.validator.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public final class EvaluationHelper {

    private EvaluationHelper() {
        // Utility class
    }

    /**
     * Parse the {@code argPath} into a {@link PathEl} array.
     *
     * @param validator the {@link Validator} instance.
     * @param contextClass the {@link Viewable} definition where the {@code argPath} starts.
     * @param argPath the path string to parse. see {@link Value#getValue()}.
     *
     * @return the parsed {@link PathEl} array or {@code null} if the {@code argPath} could not be parsed.
     */
    public static PathEl[] getAttributePathEl(Validator validator, Viewable<Element> contextClass, Value argPath) {
        try {
            ObjectPath objectPath = validator.parseObjectOrAttributePath(contextClass, argPath.getValue());
            if (objectPath.getPathElements() != null) {
                return objectPath.getPathElements();
            }
        } catch (Ili2cException e) {
            EhiLogger.logError(e);
        }
        return null;
    }

    /**
     * Get the {@link Viewable} (e.g. the class definition) from the {@link TransferDescription}.
     * If the {@code iomObject} is {@code null}, the {@code argObjects} is used to retrieve the {@link Viewable}.
     */
    public static Viewable getContextClass(TransferDescription td, IomObject iomObject, Value argObjects) {
        if (iomObject != null) {
            return (Viewable) td.getElement(iomObject.getobjecttag());
        } else if (argObjects.getViewable() != null) {
            return argObjects.getViewable();
        } else if (argObjects.getComplexObjects() != null) {
            Iterator<IomObject> it = argObjects.getComplexObjects().iterator();
            if (!it.hasNext()) {
                return null;
            }
            return (Viewable) td.getElement(it.next().getobjecttag());
        }
        return null;
    }

    /**
     * Get the collection of {@link IomObject} inside {@code argObjects} by following the provided {@code attributePath}.
     */
    public static Collection<IomObject> evaluateAttributes(Validator validator, Value argObjects, PathEl[] attributePath) {
        Collection<IomObject> attributes = new ArrayList<>();

        for (IomObject rootObject : argObjects.getComplexObjects()) {
            Value surfaceAttributes = validator.getValueFromObjectPath(null, rootObject, attributePath, null);
            if (!(surfaceAttributes.isUndefined() || surfaceAttributes.skipEvaluation() || surfaceAttributes.getComplexObjects() == null)) {
                attributes.addAll(surfaceAttributes.getComplexObjects());
            }
        }

        return attributes;
    }
}
