package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.types.OutParam;
import ch.interlis.ili2c.metamodel.*;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jtsext;
import ch.interlis.iox_j.validator.Value;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import java.util.*;
import java.util.Objects;
import java.util.function.Function;
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

        for (Map.Entry<ValueKey, Geometry> entry : geometriesByCodeValue.entrySet()) {
            entry.getValue().setUserData(entry.getKey().getStringValue());
        }

        List<Geometry> sortedGeometries;
        ValueKey firstKey = geometriesByCodeValue.keySet().iterator().next();
        Type keyType = firstKey.getType();

        if (keyType instanceof EnumerationType) {
            sortedGeometries = sortByEnumValues(geometriesByCodeValue, this::getCodeIntKey);
        } else if (keyType instanceof NumericType) {
            sortedGeometries = sortByNumericValues(geometriesByCodeValue);
        } else {
            logger.addEvent(logger.logErrorMsg("{0}: Unsupported type {1} for {2}.", usageScope, keyType.toString(), getQualifiedIliName()));
            return Value.createSkipEvaluation();
        }

        boolean result = true;
        for (int i = 0; i < sortedGeometries.size() - 1; i++) {
            Geometry current = sortedGeometries.get(i);
            Geometry next = sortedGeometries.get(i + 1);

            if (!next.contains(current)) {
                Geometry offendingGeometry = current.difference(next);
                Point centroid = offendingGeometry.getCentroid();
                String offendingCentroidWkt = centroid.toText();

                logger.addEvent(logger.logErrorMsg(
                        "IsInsideAreaByCode found an invalid overlap between code '{0}' and '{1}'. The offending geometry has it's centroid at point: {2}",
                        centroid.getX(),
                        centroid.getY(),
                        null,
                        current.getUserData().toString(),
                        next.getUserData().toString(),
                        offendingCentroidWkt));

                result = false;
            }
        }

        return new Value(result);
    }

    private List<Geometry> sortByEnumValues(Map<ValueKey, Geometry> map, Function<ValueKey, Integer> keySortOrder) {
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> keySortOrder.apply(e.getKey()),
                        Map.Entry::getValue,
                        (a, b) -> {
                            Geometry geometry = a.union(b);
                            geometry.setUserData(a.getUserData() + ", " + b.getUserData());
                            return geometry;
                        }
                ))
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private int getCodeIntKey(ValueKey key) {
        try {
            return Integer.parseInt(key.getStringValue().substring(key.getStringValue().lastIndexOf("_") + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private int getOrderedEnumIndex(ValueKey key) {
        List<String> enumValues = ((EnumerationType)key.getType()).getValues();
        return enumValues.indexOf(key.getStringValue());
    }

    private List<Geometry> sortByNumericValues(Map<ValueKey, Geometry> map) {
        return map.entrySet()
                .stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getKey().getNumericValue()))
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
