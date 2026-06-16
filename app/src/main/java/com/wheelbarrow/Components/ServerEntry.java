package com.wheelbarrow.Components;

import com.wheelbarrow.Network.ServerInfo;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

public class ServerEntry extends StackPane {
    ServerInfo info;
    public ServerEntry(ServerInfo info, Image icon) {
        this.info = info;
        this.getStyleClass().add("server-entry");
        Circle iconCircle = new Circle(20);
        iconCircle.setFill(new javafx.scene.paint.ImagePattern(icon));
        
        this.getChildren().add(iconCircle);
        this.setPrefSize(50, 50);
        this.setCursor(javafx.scene.Cursor.HAND);
    }

    public ServerEntry(ServerInfo info) {
        this.info = info;
        this.getStyleClass().add("server-entry");
        Circle iconCircle = new Circle(20);
        iconCircle.setFill(new javafx.scene.paint.Color(0.5, 0.5, 0.5, 1.0));
        
        this.getChildren().add(iconCircle);
        this.setPrefSize(50, 50);
        this.setCursor(javafx.scene.Cursor.HAND);
    }

    public ServerInfo getServerInfo() {
        return this.info;
    }

    
}