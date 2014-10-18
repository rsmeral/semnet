package xsmeral.doc;

import java.util.Map;

public class ToContextTaglet extends ObjectProcessorTaglet {

    public static void register(Map tagletMap) {
        registerInternal(new ObjectProcessorTaglet("toContext", "Context parameters supplied"), tagletMap);
    }
}
