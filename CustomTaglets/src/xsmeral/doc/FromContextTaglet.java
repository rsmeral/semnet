package xsmeral.doc;

import java.util.Map;

public class FromContextTaglet extends ObjectProcessorTaglet {

    public static void register(Map tagletMap) {
        registerInternal(new ObjectProcessorTaglet("fromContext", "Context parameters required"), tagletMap);
    }
}
