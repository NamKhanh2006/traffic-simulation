package com.myteam.traffic.ui;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MediaTestApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20px; -fx-font-size: 14px;");

        // 1. Test Hình ảnh
        Label imageLabel = new Label("Test Hình ảnh:");
        HBox imageBox = new HBox(20);
        imageBox.setAlignment(Pos.CENTER);

        try {
            java.io.InputStream carStream = getClass().getResourceAsStream("/images/car.png");
            if (carStream == null) throw new Exception("Không tìm thấy file: /images/car.png");
            ImageView carView = new ImageView(new Image(carStream));
            carView.setFitWidth(100);
            carView.setPreserveRatio(true);

            java.io.InputStream ambuStream = getClass().getResourceAsStream("/images/ambulance.png");
            if (ambuStream == null) throw new Exception("Không tìm thấy file: /images/ambulance.png");
            ImageView ambuView = new ImageView(new Image(ambuStream));
            ambuView.setFitWidth(100);
            ambuView.setPreserveRatio(true);

            java.io.InputStream fireTruckStream = getClass().getResourceAsStream("/images/firetruck.png");
            if (fireTruckStream == null) throw new Exception("Không tìm thấy file: /images/firetruck.png");
            ImageView fireTruckView = new ImageView(new Image(fireTruckStream));
            fireTruckView.setFitWidth(100);
            fireTruckView.setPreserveRatio(true);

            java.io.InputStream motorbikeStream = getClass().getResourceAsStream("/images/motorbike.png");
            if (motorbikeStream == null) throw new Exception("Không tìm thấy file: /images/motorbike.png");
            ImageView motorbikeView = new ImageView(new Image(motorbikeStream));
            motorbikeView.setFitWidth(100);
            motorbikeView.setPreserveRatio(true);

            java.io.InputStream bicycleStream = getClass().getResourceAsStream("/images/bicycle.png");
            if (bicycleStream == null) throw new Exception("Không tìm thấy file: /images/bicycle.png");
            ImageView bicycleView = new ImageView(new Image(bicycleStream));
            bicycleView.setFitWidth(100);
            bicycleView.setPreserveRatio(true);

            imageBox.getChildren().addAll(carView, ambuView, fireTruckView, motorbikeView, bicycleView);
        } catch (Exception e) {
            imageBox.getChildren().add(new Label("Lỗi tải ảnh: " + e.getMessage()));
        }

        // 2. Test Âm thanh
        Label soundLabel = new Label("Test Âm thanh (Nhấn nút để nghe):");
        HBox soundBox = new HBox(20);
        soundBox.setAlignment(Pos.CENTER);

        Button btnCar = new Button("Còi Ô tô");
        btnCar.setOnAction(e -> SoundManager.playSound("car.mp3"));

        Button btnMotorbike = new Button("Còi Xe máy");
        btnMotorbike.setOnAction(e -> SoundManager.playSound("motorbike.mp3"));

        Button btnBicycle = new Button("Chuông Xe đạp");
        btnBicycle.setOnAction(e -> SoundManager.playSound("bicycle_bell.mp3"));

        Button btnAmbu = new Button("Còi Cứu thương");
        btnAmbu.setOnAction(e -> SoundManager.playSound("ambulance_siren.mp3"));

        Button btnFireTruck = new Button("Còi Cứu hỏa");
        btnFireTruck.setOnAction(e -> SoundManager.playSound("firetruck_siren.mp3"));

        soundBox.getChildren().addAll(btnCar, btnMotorbike, btnBicycle, btnAmbu, btnFireTruck);
        root.getChildren().addAll(imageLabel, imageBox, soundLabel, soundBox);

        Scene scene = new Scene(root, 700, 350);
        primaryStage.setTitle("Test Media (Hình ảnh & Âm thanh)");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}