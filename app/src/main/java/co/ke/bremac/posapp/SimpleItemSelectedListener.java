package co.ke.bremac.posapp;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    public interface Callback {
        void selected(int position);
    }

    private final Callback callback;
    private boolean first = true;

    public SimpleItemSelectedListener(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (first) {
            first = false;
            return;
        }
        callback.selected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
