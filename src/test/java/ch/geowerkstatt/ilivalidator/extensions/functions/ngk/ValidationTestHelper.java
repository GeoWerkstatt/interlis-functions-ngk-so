package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ValidationConfig;
import ch.interlis.iox_j.validator.Validator;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class ValidationTestHelper {
    private static final String FUNCTIONS_EXT_ILI_PATH = "src/model/NGK_SO_FunctionsExt.ili";
    private static final String FUNCTIONS_EXT_23_ILI_PATH = "src/model/NGK_SO_FunctionsExt_23.ili";

    private final HashMap<String, Class<? extends InterlisFunction>> userFunctions = new HashMap<>();

    public ValidationTestHelper(InterlisFunction... userFunctions) {
        for (InterlisFunction function : userFunctions) {
            this.userFunctions.put(function.getQualifiedIliName(), function.getClass());
        }
    }

    public LogCollector runValidation(String[] dataFiles, String[] modelFiles) throws IoxException, Ili2cFailure {
        dataFiles = addLeadingTestDataDirectory(dataFiles);
        modelFiles = addLeadingTestDataDirectory(modelFiles);
        modelFiles = prependFunctionsExtIli(modelFiles);

        TransferDescription td = Ili2c.compileIliFiles(new ArrayList<>(Arrays.asList(modelFiles)), new ArrayList<String>());

        LogCollector logger = new LogCollector();
        LogEventFactory errFactory = new LogEventFactory();
        PipelinePool pool = new PipelinePool();
        Settings settings = new Settings();
        ValidationConfig modelConfig = new ValidationConfig();

        settings.setTransientObject(ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS, userFunctions);
        modelConfig.mergeIliMetaAttrs(td);
        Validator validator = new Validator(td, modelConfig, logger, errFactory, pool, settings);

        for (String filename : dataFiles) {
            IoxReader ioxReader = new ReaderFactory().createReader(new java.io.File(filename), errFactory, settings);
            if (ioxReader instanceof IoxIliReader) {
                ((IoxIliReader) ioxReader).setModel(td);

                errFactory.setDataSource(filename);
                td.setActualRuntimeParameter(ch.interlis.ili2c.metamodel.RuntimeParameters.MINIMAL_RUNTIME_SYSTEM01_CURRENT_TRANSFERFILE, filename);
                try {
                    IoxEvent event;
                    do {
                        event = ioxReader.read();
                        validator.validate(event);
                    } while (!(event instanceof EndTransferEvent));
                } finally {
                    ioxReader.close();
                }
            }
        }

        return logger;
    }

    public LogCollector runValidation(String[] modelFiles, String topic, IomObject... objects) throws Ili2cFailure {
        modelFiles = addLeadingTestDataDirectory(modelFiles);
        modelFiles = prependFunctionsExtIli(modelFiles);
        TransferDescription td = Ili2c.compileIliFiles(new ArrayList<>(Arrays.asList(modelFiles)), new ArrayList<String>());

        LogCollector logger = new LogCollector();
        LogEventFactory errFactory = new LogEventFactory();
        PipelinePool pool = new PipelinePool();
        Settings settings = new Settings();
        ValidationConfig modelConfig = new ValidationConfig();

        settings.setTransientObject(ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS, userFunctions);
        modelConfig.mergeIliMetaAttrs(td);
        Validator validator = new Validator(td, modelConfig, logger, errFactory, pool, settings);

        validator.validate(new StartTransferEvent());
        validator.validate(new StartBasketEvent(topic, "b1"));
        for (IomObject object : objects) {
            validator.validate(new ObjectEvent(object));
        }
        validator.validate(new EndBasketEvent());
        validator.validate(new ch.interlis.iox_j.EndTransferEvent());
        return logger;
    }

    private String[] prependFunctionsExtIli(String[] modelDirs) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        String[] result = new String[modelDirs.length + 1];
        result[0] = FUNCTIONS_EXT_ILI_PATH;
        System.arraycopy(modelDirs, 0, result, 1, modelDirs.length);

        return result;
    }

    private String[] addLeadingTestDataDirectory(String[] files) {
        return Arrays
                .stream(files).map(file -> Paths.get("src/test/data", file).toString())
                .distinct()
                .toArray(String[]::new);
    }
}
