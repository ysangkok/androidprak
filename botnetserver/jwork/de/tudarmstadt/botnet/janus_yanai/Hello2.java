package de.tudarmstadt.botnet.janus_yanai;
import android.util.Log;
public class Hello2 implements java.util.concurrent.Callable<String> {
    public String call() {
        Log.d("LOL", "GUIDO GUIDO");
        return "ok, executed";
    }
}
