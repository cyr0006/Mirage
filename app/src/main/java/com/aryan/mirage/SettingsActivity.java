package com.aryan.mirage;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aryan.mirage.data.ForceState;

import java.util.function.Consumer;

/**
 * Hidden activity, reached only via HiddenControls' long-press gesture. See SPEC.md
 * "2. SettingsActivity".
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int CARD_GRID_COLUMNS = 4;

    private ForceState forceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        forceState = new ForceState(this);

        RecyclerView cardGrid = findViewById(R.id.cardGrid);
        cardGrid.setLayoutManager(new GridLayoutManager(this, CARD_GRID_COLUMNS));
        cardGrid.setAdapter(new CardGridAdapter(forceState.getForcedCard(), forceState::setForcedCard));

        setUpSpinner(R.id.modeSpinner, ForceState.Mode.values(), forceState.getMode(), forceState::setMode);
        setUpSpinner(R.id.triggerSpinner, ForceState.Trigger.values(), forceState.getTrigger(), forceState::setTrigger);

        // TODO: sliders and the debug-overlay switch, same persist-on-change pattern.
    }

    /** Both Mode and Trigger spinners follow the exact same pattern: populate from an
     * enum's values(), select whatever's currently persisted, and write through to
     * ForceState the moment the user picks something else. */
    private <E extends Enum<E>> void setUpSpinner(int spinnerId, E[] values, E current,
                                                   Consumer<E> onSelected) {
        Spinner spinner = findViewById(spinnerId);
        ArrayAdapter<E> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setSelection(adapter.getPosition(current));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSelected.accept(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
