package org.labkey.panoramapublic.proteomexchange;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class Formula
{
    private final TreeMap<ChemElement, Integer> _elementCounts; // Map is sorted on natural ordering of ChemElement

    public Formula()
    {
        _elementCounts = new TreeMap<>();
    }

    public void addElement(@NotNull ChemElement element, int count)
    {
        Integer currentCount = _elementCounts.computeIfAbsent(element, el -> 0);
        int newCount = currentCount + count;
        if (newCount != 0)
        {
            _elementCounts.put(element, newCount);
        }
        else
        {
            _elementCounts.remove(element);
        }
    }

    public SortedMap<ChemElement, Integer> getElementCounts()
    {
        return Collections.unmodifiableSortedMap(_elementCounts);
    }

    public @NotNull String getFormula()
    {
        StringBuilder positive = new StringBuilder();
        StringBuilder negative = new StringBuilder();
        for (ChemElement el: _elementCounts.keySet())
        {
            int count = _elementCounts.get(el);
            boolean pos = count > 0;
            count = count < 0 ? count * -1 : count;
            String formulaPart = el.getSymbol() + (count > 1 ? count : "");
            if (pos)
            {
                positive.append(formulaPart);
            }
            else
            {
                negative.append(formulaPart);
            }
        }
        String separator = positive.length() > 0 && negative.length() > 0 ? " - " : (negative.length() > 0 ? "-" : "");
        return positive + separator + negative;
    }

    public Formula addFormula(@NotNull Formula otherFormula)
    {
        Formula newFormula = new Formula();

        this._elementCounts.forEach(newFormula::addElement);
        otherFormula._elementCounts.forEach(newFormula::addElement);

        return newFormula;
    }

    public Formula subtractFormula(@NotNull Formula otherFormula)
    {
        Formula newFormula = new Formula();

        this._elementCounts.forEach(newFormula::addElement);
        otherFormula._elementCounts.forEach((key, value) -> newFormula.addElement(key, value * -1));

        return newFormula;
    }

    public boolean isEmpty()
    {
        return _elementCounts.isEmpty();
    }

    @Override
    public String toString()
    {
        return getFormula();
    }

    /**
     * @param input formula to normalize
     * @return Returns the normalized formula of the form H'6C'8N'4 - H2C6N4.
     * Elements in the formula are ordered according to the order of {@link ChemElement}.
     * Returns the input formula if there is an error in parsing the formula.
     */
    public static @NotNull String normalizeFormula(String input)
    {
        Formula formula = tryParse(input, new ArrayList<>());
        return formula != null ? formula.getFormula() : input;
    }

    /**
     * @param input formula to normalize
     * @return Returns the normalized formula of the form H'6C'8N'4 - H2C6N4.
     * Elements in the formula are ordered according to the order of {@link ChemElement}.
     * Returns null if there is an error in parsing the input formula.
     */
    public static @Nullable String normalizeIfValid(String input)
    {
        Formula formula = tryParse(input, new ArrayList<>());
        return formula != null ? formula.getFormula() : null;
    }

    /**
     * Parses a formula of the form H'6C'8N'4 - H2C6N4. Returns null if exceptions were thrown while parsing the formula.
     * The error messages are added to the given errors parameter.
     * @param input formula to parse
     * @param errors parse error messages
     * @return a Formula object representing the input formula, or null if there were errors in parsing the formula
     */
    public static @Nullable Formula tryParse(String input, @NotNull List<String> errors)
    {
        try
        {
            return parse(input);
        }
        catch (IllegalArgumentException ex)
        {
            errors.add("Failed to parse formula '" + input + "'. " + ex.getMessage());
        }
        return null;
    }

    /**
     * Parses a formula of the form H'6C'8N'4 - H2C6N4. An exception is throws if there are more than one subtraction
     * operations in the input formula (e.g. H2C'6 - N4 - C6) there are unrecognized elements in the formula or if
     * the formula contains unrecognized elements.
     * @param input formula to parse
     * @return a Formula object representing the input formula
     */
    public static @NotNull Formula parse(String input)
    {
        return parse(input, new Formula(), false);
    }

    // Based on BiomassCalc.ParseCounts() in Skyline.
    // Other related code:
    // BiomassCalc.ParseMassExpression() and BiomassCalc.ParseMass()
    // Formula.ParseToDictionary()
    private static Formula parse(String input, Formula formula, boolean negative)
    {
        if(StringUtils.isBlank(input))
        {
            return formula != null ? formula : new Formula();
        }

        if (formula == null) formula = new Formula();

        // Assume formulas are of the form H'6C'8N'4 - H2C6N4.
        // The part of the formula following ' - ' are the element masses that will be subtracted
        // from the total mass.  Only one negative part is allowed. We will parse the positive and negative parts separately.
        input = input.trim();

        while (input.length() > 0)
        {
            if (input.startsWith("-"))
            {
                if (negative)
                {
                    throw new IllegalArgumentException("More than one subtraction operation is not supported in a formula.");
                }
                return parse(input.substring(1), formula, true);
            }
            String sym = getNextSymbol(input);
            ChemElement el = ChemElement.getElementForSymbol(sym);

            // Unrecognized element
            if (el == null)
            {
                throw new IllegalArgumentException("Unrecognized element in formula: " + sym);
            }

            input = input.substring(sym.length());
            int endCount = 0;
            while (endCount < input.length() && Character.isDigit(input.charAt(endCount)))
            {
                endCount++;
            }

            int count = endCount == 0 ? 1 : Integer.parseInt(input.substring(0, endCount));

            if (negative)
            {
                count = -count;
            }

            formula.addElement(el, count);
            input = input.substring(endCount).trim();
        }
        return formula;
    }

    private static String getNextSymbol(String input)
    {
        // Skip the first character, since it is always the start of
        // the symbol, and then look for the end of the symbol.
        for (int i = 1; i < input.length(); i++)
        {
            char c = input.charAt(i);
            if (!Character.isLowerCase(c) && c != '\'' && c != '"')
            {
                return input.substring(0, i);
            }
        }
        return input;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFormula()
        {
            assertTrue(Formula.parse(null).isEmpty());
            assertTrue(Formula.parse("").isEmpty());

            var C12H8S2O6 = "C12H8S2O6";
            var SO4 = "SO4";
            assertEquals("H8C12O6S2", Formula.parse(C12H8S2O6).getFormula());
            var subtracted = C12H8S2O6+ " - " +SO4;
            assertEquals("H8C12O2S", Formula.parse(subtracted).getFormula());
            assertEquals("H8C12O2S", Formula.parse(C12H8S2O6).subtractFormula(Formula.parse(SO4)).getFormula());
            assertEquals("-O4S",  new Formula().subtractFormula(Formula.parse(SO4)).getFormula());
            assertEquals("O4S",  Formula.parse(SO4).addFormula(new Formula()).getFormula());

            var formula = new Formula();
            formula.addElement(ChemElement.C, 3);
            formula.addElement(ChemElement.H, 8);
            formula.addElement(ChemElement.O, 2);
            formula.addElement(ChemElement.C13, 3);
            formula.addElement(ChemElement.N, 0);
            assertEquals("H8C3C'3O2", formula.getFormula());
            // H4C3O + H4C'3O = H8C3C'3O2
            assertEquals(formula.getFormula(), Formula.parse("H4C3O").addFormula(Formula.parse("H4C'3O")).getFormula());

            var errors = new ArrayList<String>();
            var input = subtracted + subtracted;
            formula = Formula.tryParse(input, errors); // More than one subtraction operation is not supported
            assertNull(formula);
            assertEquals(1, errors.size());
            assertEquals("Failed to parse formula '" + input + "'. More than one subtraction operation is not supported in a formula.", errors.get(0));

            errors = new ArrayList<>();
            input = C12H8S2O6 + 'X' + SO4;
            formula = Formula.tryParse(input, errors); // Unrecognized element in formula
            assertNull(formula);
            assertEquals("Failed to parse formula '" + input + "'. Unrecognized element in formula: X", errors.get(0));


            // Check our ability to handle strangely constructed chemical formulas (from MassCalcTest.TestGetIonFormula() in Skyline)
            var normalized = "H9C12S2";
            assertEquals(Formula.parse("C12H9S2P0").getFormula(), Formula.parse("C12H9S2").getFormula()); // P0 is weird
            assertEquals(normalized, Formula.parse("C12H9S2P0").getFormula());
            normalized = "H9C12PS2";
            assertEquals(Formula.parse("C12H9S2P1").getFormula(), Formula.parse("C12H9S2P").getFormula()); // P1 is weird
            assertEquals(normalized, Formula.parse("C12H9S2P1").getFormula());
            normalized = "H9C12P";
            assertEquals(Formula.parse("C12H9S0P").getFormula(), Formula.parse("C12H9P").getFormula()); // S0 is weird, and not at end
            assertEquals(normalized, Formula.parse("C12H9S0P").getFormula());

            assertEquals("Cl", Formula.parse("Cl1").getFormula());
            assertEquals("-Cl", Formula.parse(" - Cl1").getFormula());
            assertEquals("O2 - H2N2", Formula.parse("OO-HNHN").getFormula());
            assertEquals("O - H2N2", Formula.parse("OO-HNHNO").getFormula());
            assertEquals("C6N2 - C'6N'2", Formula.parse("C5NNC - C'N'C'5N'").getFormula());
            assertEquals("C7N2Cl - C'6N'2", Formula.parse("C5NNCClC - C'N'C'5N'").getFormula());
        }
    }
}
