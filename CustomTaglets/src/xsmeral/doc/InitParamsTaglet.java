package xsmeral.doc;

import java.util.Map;

public class InitParamsTaglet extends ObjectProcessorTaglet {
    
    public static void register(Map tagletMap) {
        registerInternal(new ObjectProcessorTaglet("init", "Initialization parameters"), tagletMap);
    }
}
