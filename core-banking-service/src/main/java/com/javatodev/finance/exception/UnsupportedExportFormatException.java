package com.javatodev.finance.exception;

public class UnsupportedExportFormatException extends SimpleBankingGlobalException {
    public UnsupportedExportFormatException(String format) {
        super("Unsupported statement export format: '" + format + "'. Supported formats are CSV and JSON.",
            GlobalErrorCode.UNSUPPORTED_EXPORT_FORMAT);
    }
}
