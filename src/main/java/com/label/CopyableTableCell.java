package com.label;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** 只读字段：允许拖选文字，复制部分内容或完整字段，并保留原始换行。 */
class CopyableTableCell<S, T> extends TableCell<S, T> {
    private final TextArea field = new TextArea();
    private final Tooltip tooltip = new Tooltip();

    CopyableTableCell() {
        getStyleClass().add("copyable-table-cell");
        field.getStyleClass().add("copyable-table-text");
        field.setEditable(false);
        field.setWrapText(false);
        field.setPrefRowCount(1);
        field.setMinWidth(0);
        field.prefWidthProperty().bind(widthProperty().subtract(8));
        // 只跟随页面内的焦点切换，右键菜单弹出时仍保留待复制的选区。
        ChangeListener<Node> focusOwnerListener = (observable, previous, current) -> {
            if (previous == field && current != field) {
                field.deselect();
            }
        };
        field.sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null) previous.focusOwnerProperty().removeListener(focusOwnerListener);
            field.deselect();
            if (current != null) current.focusOwnerProperty().addListener(focusOwnerListener);
        });
        field.skinProperty().addListener((observable, oldSkin, newSkin) -> {
            ScrollPane scrollPane = (ScrollPane) field.lookup(".scroll-pane");
            if (scrollPane != null) {
                // 单行表格不留滚动条占位，长文本仍可用选区、方向键和右键完整复制。
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }
        });
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(520);
        tooltip.textProperty().bind(field.textProperty());

        MenuItem copySelection = new MenuItem("复制选中文字");
        copySelection.setOnAction(event -> copy(field.getSelectedText()));
        MenuItem copyField = new MenuItem("复制完整字段");
        copyField.setOnAction(event -> copy(field.getText()));
        MenuItem selectAll = new MenuItem("全选");
        selectAll.setOnAction(event -> field.selectAll());
        ContextMenu menu = new ContextMenu(copySelection, copyField, new SeparatorMenuItem(), selectAll);
        menu.setOnShowing(event -> {
            copySelection.setDisable(field.getSelectedText().isEmpty());
            copyField.setDisable(field.getText().isEmpty());
            selectAll.setDisable(field.getText().isEmpty());
        });
        field.setContextMenu(menu);
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
                String selection = field.getSelectedText();
                copy(selection.isEmpty() ? field.getText() : selection);
                event.consume();
            }
        });
    }

    protected String formatItem(T item) {
        return item == null ? "" : item.toString();
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        String value = empty ? "" : formatItem(item);
        if (!field.getText().equals(value)) {
            field.setText(value);
            field.positionCaret(0);
            field.setScrollTop(0);
            field.setScrollLeft(0);
        }
        field.setTooltip(empty || value.isEmpty() ? null : tooltip);
        setGraphic(empty ? null : field);
    }

    private static void copy(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
