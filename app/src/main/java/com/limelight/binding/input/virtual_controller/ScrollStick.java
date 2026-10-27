package com.limelight.binding.input.virtual_controller;

/** A stick that drives the mouse (see ScrollStickDriver) instead of a gamepad stick. */
interface ScrollStick {
    ScrollStickDriver getDriver();
}
