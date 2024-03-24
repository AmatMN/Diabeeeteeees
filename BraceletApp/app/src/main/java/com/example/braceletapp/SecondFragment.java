package com.example.braceletapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import androidx.fragment.app.Fragment;

public class SecondFragment extends Fragment {
    TextView connView;
    TextView capView;
    String current = "Scanning…";
    String captured = "Scanned Connections:";
    public SecondFragment(){
        // require a empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    /*
    This function updates the second fragment with the data last received through setData()
    Scanning end is whether or not the bracelet is currently connected and providing data
    */
    public void update(boolean scanningEnd){
        if (!scanningEnd){
            current = "Scanning…";
        }
        if (getView() != null) {
            connView = (TextView) getView().findViewById(R.id.current_connections);
            connView.setText(current);
            capView = (TextView) getView().findViewById(R.id.available_conn);
            capView.setText(captured);
        }
    }

    /*
    setData is called whenever the app connected with a device
    and changes the text to be displayed in the "scanning..." part of the fragment
    to the name of the device that is currently connected
    name is the name and address of the connected device
    */
    @SuppressLint("SetTextI18n")
    public void setData(String name){
        if (getView() != null) {
            current = "currently connected with: " + name;
            connView = (TextView) getView().findViewById(R.id.current_connections);
            connView.setText(current);
        }
    }

    /*
    Set the data to be used by the update and thus displayed after the next time update is called
    Names is the list of names and addresses already found
    */
    @SuppressLint("SetTextI18n")
    public void scanLog(List<String> names){
        if (getView() != null){
            captured = "Scanned Connections:";
            for (int i = 0; i < names.size(); i++) {
                captured += "\n" + names.get(i);
            }
            capView = (TextView) getView().findViewById(R.id.available_conn);
            capView.setText("");
            capView.append(captured);
        }
    }
}