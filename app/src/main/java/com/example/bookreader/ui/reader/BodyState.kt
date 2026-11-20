package com.example.bookreader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Связь между оболочкой читалки (меню, жесты, ползунок) и телом, которое показывает текст
 * (страницы или лента). Тело публикует позицию и принимает команды.
 */
class BodyState(startBlock: Int, startChar: Int) {
    var block by mutableIntStateOf(startBlock)
    var char by mutableIntStateOf(startChar)
    var fraction by mutableFloatStateOf(0f)
    var pageText by mutableStateOf("")

    var turn: (Int) -> Unit = {}
    var jumpBlock: (Int) -> Unit = {}
    var jumpFraction: (Float) -> Unit = {}
}
