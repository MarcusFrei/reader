package com.example.bookreader.domain

/** Таблица «зона × жест → действие». Пользователь редактирует её в настройках. */
enum class Zone(val label: String) {
    LEFT_EDGE("Левый край"),
    LEFT("Левая часть"),
    CENTER("Центр"),
    RIGHT("Правая часть"),
    RIGHT_EDGE("Правый край")
}

enum class Gesture(val label: String) {
    TAP("Касание"),
    DOUBLE_TAP("Двойное касание"),
    LONG_PRESS("Долгое нажатие"),
    VERTICAL_DRAG("Свайп вверх/вниз")
}

enum class ReaderAction(val label: String, val continuous: Boolean = false) {
    NONE("Нет действия"),
    NEXT_PAGE("Следующая страница"),
    PREV_PAGE("Предыдущая страница"),
    TOGGLE_UI("Показать/скрыть меню"),
    ZOOM_TOGGLE("Быстрый масштаб"),
    OPEN_TOC("Оглавление"),
    OPEN_SETTINGS("Настройки"),
    TOGGLE_THEME("Сменить тему"),
    BRIGHTNESS("Яркость", true),
    FONT_SIZE("Размер шрифта", true)
}

fun gestureKey(zone: Zone, gesture: Gesture) = "${zone.name}|${gesture.name}"

fun defaultGestures(): Map<String, String> = mapOf(
    gestureKey(Zone.LEFT_EDGE, Gesture.VERTICAL_DRAG) to ReaderAction.BRIGHTNESS.name,
    gestureKey(Zone.RIGHT_EDGE, Gesture.VERTICAL_DRAG) to ReaderAction.FONT_SIZE.name,
    gestureKey(Zone.LEFT_EDGE, Gesture.TAP) to ReaderAction.PREV_PAGE.name,
    gestureKey(Zone.LEFT, Gesture.TAP) to ReaderAction.PREV_PAGE.name,
    gestureKey(Zone.CENTER, Gesture.TAP) to ReaderAction.TOGGLE_UI.name,
    gestureKey(Zone.CENTER, Gesture.DOUBLE_TAP) to ReaderAction.ZOOM_TOGGLE.name,
    gestureKey(Zone.CENTER, Gesture.LONG_PRESS) to ReaderAction.OPEN_SETTINGS.name,
    gestureKey(Zone.RIGHT, Gesture.TAP) to ReaderAction.NEXT_PAGE.name,
    gestureKey(Zone.RIGHT_EDGE, Gesture.TAP) to ReaderAction.NEXT_PAGE.name
)

fun zoneOf(x: Float, width: Float): Zone = when {
    x < width * 0.10f -> Zone.LEFT_EDGE
    x > width * 0.90f -> Zone.RIGHT_EDGE
    x < width * 0.33f -> Zone.LEFT
    x > width * 0.67f -> Zone.RIGHT
    else -> Zone.CENTER
}
