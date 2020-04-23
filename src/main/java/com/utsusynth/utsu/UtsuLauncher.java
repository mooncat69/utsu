package com.utsusynth.utsu;

public class UtsuLauncher {
    public static void main(String[] args) {
        new Thread() {
            @Override
            public void run() {
                javafx.application.Application.launch(UtsuApp.class);
            }
        }.start();
    }
}