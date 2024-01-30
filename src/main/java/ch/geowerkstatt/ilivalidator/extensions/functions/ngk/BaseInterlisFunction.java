package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxValidationConfig;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.validator.Validator;
import ch.interlis.iox_j.validator.Value;

public abstract class BaseInterlisFunction implements InterlisFunction {

    protected LogEventFactory logger;
    protected TransferDescription td;
    protected Settings settings;
    protected Validator validator;
    protected ObjectPool objectPool;

    @Override
    public final void init(TransferDescription td, Settings settings, IoxValidationConfig validationConfig, ObjectPool objectPool, LogEventFactory logEventFactory) {
        this.logger = logEventFactory;
        this.logger.setValidationConfig(validationConfig);
        this.td = td;
        this.settings = settings;
        this.validator = (Validator) settings.getTransientObject(IOX_VALIDATOR);
        this.objectPool = objectPool;
    }

    @Override
    public final Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {

        for (Value arg : actualArguments) {
            if (arg.skipEvaluation()) {
                return arg;
            }
        }

        logger.setDataObj(mainObj);

        return evaluateInternal(validationKind, usageScope, mainObj, actualArguments);
    }

    protected abstract Value evaluateInternal(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments);
}
