package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.types.OutParam;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.PathEl;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jtsext;
import ch.interlis.iox_j.validator.Value;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class IsInsideAreaByCodeIoxPlugin extends BaseInterlisFunction {
    private static final Map<InsideAreaKey, Value> OBJECTS_CACHE = new HashMap<>();

    @Override
    public String getQualifiedIliName() {
        return "NGK_SO_FunctionsExt.IsInsideAreaByCode";
    }

    @Override
    protected Value evaluateInternal(String validationKind, String usageScope, IomObject contextObject, Value[] arguments) {
        Value argObjects = arguments[0];
        Value argGeometryPath = arguments[1];
        Value argCodePath = arguments[2];

        if (argObjects.isUndefined() || argGeometryPath.isUndefined() || argCodePath.isUndefined()) {
            return Value.createSkipEvaluation();
        }

        Collection<IomObject> objects = argObjects.getComplexObjects();
        if (objects.isEmpty()) {
            return new Value(true);
        }

        List<String> objectIds = objects.stream().map(IomObject::getobjectoid).collect(Collectors.toList());
        String geometryAttribute = argGeometryPath.getValue();
        String codeAttribute = argCodePath.getValue();

        InsideAreaKey key = new InsideAreaKey(objectIds, geometryAttribute, codeAttribute);
        return OBJECTS_CACHE.computeIfAbsent(key, k -> {
            Viewable contextClass = EvaluationHelper.getContextClass(td, contextObject, argObjects);
            if (contextClass == null) {
                throw new IllegalStateException("unknown class in " + usageScope);
            }

            PathEl[] geometryPath = EvaluationHelper.getAttributePathEl(validator, contextClass, argGeometryPath);
            PathEl[] codePath = EvaluationHelper.getAttributePathEl(validator, contextClass, argCodePath);

            return isInsideArea(usageScope, objects, geometryPath, codePath);
        });
    }

    private Value isInsideArea(String usageScope, Collection<IomObject> objects, PathEl[] geometryPath, PathEl[] codePath) {
        Map<ValueKey, Geometry> geometriesByCodeValue = objects.stream()
                .collect(Collectors.toMap(
                        o -> getCodeValue(o, codePath),
                        o -> getGeometryValue(o, geometryPath),
                        Geometry::union
                ));

        ValueKey firstKey = geometriesByCodeValue.keySet().iterator().next();
        Type keyType = firstKey.getType();
        if (!(keyType instanceof EnumerationType)) {
            logger.addEvent(logger.logErrorMsg("{0}: Enumeration type expected.", usageScope));
            return Value.createSkipEvaluation();
        }
        EnumerationType enumType = (EnumerationType) keyType;
        if (!enumType.isOrdered()) {
            logger.addEvent(logger.logErrorMsg("{0}: Enumeration type must be ordered.", usageScope));
            return Value.createSkipEvaluation();
        }

        List<Geometry> sortedGeometries = sortByEnumValues(geometriesByCodeValue, enumType);
        for (int i = 0; i < sortedGeometries.size() - 1; i++) {
            Geometry current = sortedGeometries.get(i);
            Geometry next = sortedGeometries.get(i + 1);

            if (!next.contains(current)) {
                return new Value(false);
            }
        }

        return new Value(true);
    }

    private List<Geometry> sortByEnumValues(Map<ValueKey, Geometry> map, EnumerationType enumType) {
        List<String> enumValues = enumType.getValues();

        return map.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> enumValues.indexOf(entry.getKey().getStringValue())))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private ValueKey getCodeValue(IomObject object, PathEl[] enumPath) {
        Value value = validator.getValueFromObjectPath(null, object, enumPath, null);
        return new ValueKey(value);
    }

    private Geometry getGeometryValue(IomObject object, PathEl[] geometryPath) {
        Value objects = new Value(Collections.singletonList(object));
        Collection<IomObject> geometryObjects = EvaluationHelper.evaluateAttributes(validator, objects, geometryPath);

        List<Geometry> geometries = geometryObjects.stream()
                .map(g -> {
                    try {
                        return Iox2jtsext.multisurface2JTS(g, 0, new OutParam<>(), logger, 0, "warning");
                    } catch (Exception e) {
                        logger.addEvent(logger.logWarningMsg("{0}: Failed to convert surface to JTS: {1}", getQualifiedIliName(), e.getLocalizedMessage()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (geometries.size() == 1) {
            return geometries.get(0);
        } else {
            return new GeometryFactory().buildGeometry(geometries);
        }
    }

    private static final class ValueKey {
        private final Value value;

        ValueKey(Value value) {
            this.value = value;
        }

        public Type getType() {
            return value.getType();
        }

        public String getStringValue() {
            return value.getValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ValueKey)) {
                return false;
            }
            ValueKey that = (ValueKey) o;
            return Objects.equals(getStringValue(), that.getStringValue())
                    && Objects.equals(getType(), that.getType());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getStringValue());
        }
    }

    private static final class InsideAreaKey {
        private final List<String> objectIds;
        private final String geometryAttribute;
        private final String codeAttribute;

        InsideAreaKey(List<String> objectIds, String geometryAttribute, String codeAttribute) {
            this.objectIds = objectIds;
            this.geometryAttribute = geometryAttribute;
            this.codeAttribute = codeAttribute;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InsideAreaKey)) {
                return false;
            }
            InsideAreaKey that = (InsideAreaKey) o;
            return objectIds.equals(that.objectIds)
                    && geometryAttribute.equals(that.geometryAttribute)
                    && codeAttribute.equals(that.codeAttribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectIds, geometryAttribute, codeAttribute);
        }
    }
}
