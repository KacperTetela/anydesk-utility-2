package raven.anydesk;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class AnyDeskId {

    private AnyDeskId() {
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return false;
        }
        return s.matches("[0-9 ]+") && s.matches(".*\\d.*");
    }

    public static DocumentFilter digitsAndSpacesFilter() {
        return new DigitsAndSpacesFilter();
    }

    private static final class DigitsAndSpacesFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) {
                return;
            }
            super.insertString(fb, offset, string.replaceAll("[^0-9 ]", ""), attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            super.replace(fb, offset, length, text.replaceAll("[^0-9 ]", ""), attrs);
        }
    }
}

