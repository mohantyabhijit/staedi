package io.xlate.edi.internal.stream.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import io.xlate.edi.internal.stream.tokenization.CharacterSet;
import io.xlate.edi.internal.stream.tokenization.Dialect;
import io.xlate.edi.internal.stream.tokenization.DialectFactory;
import io.xlate.edi.internal.stream.tokenization.EDIException;
import io.xlate.edi.schema.EDISimpleType;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamValidationError;
import io.xlate.edi.stream.EDIValidationException;

class AlphaNumericValidatorTest implements ValueSetTester {

    Dialect dialect;

    @BeforeEach
    void setUp() throws EDIException {
        dialect = DialectFactory.getDialect("UNA");
        CharacterSet chars = new CharacterSet();
        "UNA=*.?^~UNB*UNOA=3*005435656=1*006415160=1*060515=1434*00000000000778~".chars().forEach(c -> dialect.appendHeader(chars, (char) c));
    }

    @Test
    void testValidateLengthTooShort() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(5L);
        when(element.getMaxLength()).thenReturn(5L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = AlphaNumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "TEST", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_SHORT, errors.get(0));
    }

    @Test
    void testValidateLengthTooLong() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(5L);
        when(element.getMaxLength()).thenReturn(5L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = AlphaNumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "TESTTEST", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_LONG, errors.get(0));
    }

    @Test
    void testValidateValueNotInSet() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(5L);
        when(element.getValueSet()).thenReturn(setOf("VAL1", "VAL2"));
        ElementValidator v = AlphaNumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "TEST", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_CODE_VALUE, errors.get(0));
    }

    @Test
    void testValidateValueInSetBadCharacter() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(5L);
        when(element.getValueSet()).thenReturn(setOf("VAL1", "VAL\u0008"));
        ElementValidator v = AlphaNumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "VAL\u0008", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_CHARACTER_DATA, errors.get(0));
    }

    @Test
    void testFormatValueTooLong() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(5L);
        ElementValidator v = AlphaNumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        EDIValidationException e = assertThrows(EDIValidationException.class, () -> v.format(dialect, element, "TESTTEST", output));
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_LONG, e.getError());
    }

    @Test
    void testFormatValueNotInSet() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf("VAL1", "VAL2"));
        ElementValidator v = AlphaNumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        EDIValidationException e = assertThrows(EDIValidationException.class, () -> v.format(dialect, element, "TESTTEST", output));
        assertEquals(EDIStreamValidationError.INVALID_CODE_VALUE, e.getError());
    }

    @Test
    void testFormatValueInSet() throws EDIException {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf("VAL1", "VAL2"));
        ElementValidator v = AlphaNumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        v.format(dialect, element, "VAL1", output);
        assertEquals("VAL1", output.toString());
    }

    @Test
    void testFormatInvalidCharacterData() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(4L);
        ElementValidator v = AlphaNumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "TES\u0008", output);
            fail("Exception was expected");
        } catch (EDIException e) {
            assertTrue(e.getMessage().startsWith("EDIE004"));
        }
    }

    @Test
    void testFormatValidValuePaddedLength() throws EDIException {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(10L);
        when(element.getMaxLength()).thenReturn(10L);
        ElementValidator v = AlphaNumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        v.format(dialect, element, "TEST", output);
        assertEquals("TEST      ", output.toString());
    }

    @Test
    void testFormatValidValueIOException() throws Exception {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength(anyString())).thenCallRealMethod();
        when(element.getMaxLength(anyString())).thenCallRealMethod();
        when(element.getValueSet(anyString())).thenCallRealMethod();

        when(element.getMinLength()).thenReturn(10L);
        when(element.getMaxLength()).thenReturn(10L);
        ElementValidator v = AlphaNumericValidator.getInstance();
        Appendable output = mock(Appendable.class);
        when(output.append(ArgumentMatchers.anyChar())).thenThrow(IOException.class);
        EDIStreamException thrown = assertThrows(EDIStreamException.class, () -> v.format(dialect, element, "TEST", output));
        assertEquals(IOException.class, thrown.getCause().getClass());
    }
}
