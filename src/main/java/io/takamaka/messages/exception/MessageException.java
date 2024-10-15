/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class MessageException extends Exception {

    private static final long serialVersionUID = 1L;

    public MessageException() {
        super();
    }

    public MessageException(String msg) {
        super(msg);
    }

    public MessageException(Throwable er) {
        super(er);
    }

    public MessageException(String msg, Throwable er) {
        super(msg, er);
    }
}
