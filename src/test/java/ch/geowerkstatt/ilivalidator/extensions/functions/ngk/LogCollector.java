package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.LogEvent;
import ch.interlis.iox.IoxLogEvent;

import java.util.ArrayList;


public final class LogCollector implements ch.interlis.iox.IoxLogging {
    private final ArrayList<IoxLogEvent> errs = new ArrayList<>();
    private final ArrayList<IoxLogEvent> warn = new ArrayList<>();

    @Override
    public void addEvent(IoxLogEvent event) {
        EhiLogger.getInstance().logEvent((LogEvent) event);
        if (event.getEventKind() == IoxLogEvent.ERROR) {
            errs.add(event);
        } else if (event.getEventKind() == IoxLogEvent.WARNING) {
            warn.add(event);
        }
    }

    public ArrayList<IoxLogEvent> getErrs() {
        return errs;
    }

    public ArrayList<IoxLogEvent> getWarn() {
        return warn;
    }
}
