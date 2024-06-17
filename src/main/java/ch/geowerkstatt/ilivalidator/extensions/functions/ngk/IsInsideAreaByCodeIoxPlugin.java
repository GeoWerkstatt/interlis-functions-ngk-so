package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.types.OutParam;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PathEl;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jtsext;
import ch.interlis.iox_j.validator.Value;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

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

        List<Map.Entry<ValueKey, Geometry>> sortedGeometries;
        ValueKey firstKey = geometriesByCodeValue.keySet().iterator().next();
        Type keyType = firstKey.getType();

        if (keyType instanceof EnumerationType) {
            EnumerationType enumType = (EnumerationType) keyType;
            if (!enumType.isOrdered()) {
                logger.addEvent(logger.logErrorMsg("{0}: Enumeration type must be ordered.", usageScope));
                return Value.createSkipEvaluation();
            }
            sortedGeometries = sortByEnumValues(geometriesByCodeValue, enumType);
        } else if (keyType instanceof NumericType) {
            sortedGeometries = sortByNumericValues(geometriesByCodeValue);
        } else {
            logger.addEvent(logger.logErrorMsg("{0}: Unsupported type {1} for {2}.", usageScope, keyType.toString(), getQualifiedIliName()));
            return Value.createSkipEvaluation();
        }

        boolean result = true;
        for (int i = 0; i < sortedGeometries.size() - 1; i++) {
            Map.Entry<ValueKey, Geometry> current = sortedGeometries.get(i);
            Map.Entry<ValueKey, Geometry> next = sortedGeometries.get(i + 1);

            if (!next.getValue().contains(current.getValue())) {
                Geometry offendingGeometry = current.getValue().difference(next.getValue());
                Point centroid = offendingGeometry.getCentroid();
                String offendingCentroidWkt = centroid.toText();

                String currentCode = current.getKey().getStringValue();
                String nextCode = next.getKey().getStringValue();

                logger.addEvent(logger.logErrorMsg(
                        "IsInsideAreaByCode found an invalid overlap between code '{0}' and '{1}'. The offending geometry has it's centroid at point: {2}",
                        centroid.getX(),
                        centroid.getY(),
                        null,
                        currentCode,
                        nextCode,
                        offendingCentroidWkt));

                result = false;
            }
        }

        return new Value(result);
    }

    private List<Map.Entry<ValueKey, Geometry>> sortByEnumValues(Map<ValueKey, Geometry> map, EnumerationType enumType) {
        List<String> enumValues = enumType.getValues();

        return map.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> enumValues.indexOf(entry.getKey().getStringValue())))
                .collect(Collectors.toList());
    }

    private List<Map.Entry<ValueKey, Geometry>> sortByNumericValues(Map<ValueKey, Geometry> map) {
        return map.entrySet()
                .stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getKey().getNumericValue()))
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

        public double getNumericValue() {
            return value.getNumeric();
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
