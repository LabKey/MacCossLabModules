package org.labkey.panoramapublic.proteomexchange;

import java.util.HashMap;
import java.util.Map;

public enum ChemElement
{
    // Source: Unimod
    H("H",1.00794),
    H2("2H", "H'", 2.014101779),
    Li("Li",6.941),
    B("B",10.811),
    C("C",12.0107),
    C13("13C","C'", 13.00335483),
    N("N",14.0067),
    N15("15N","N'", 15.00010897),
    O("O",15.9994),
    O18("18O", "O'", 17.9991603),
    F("F",18.9984032),
    Na("Na",22.98977),
    Mg("Mg",24.305),
    Al("Al",26.9815386),
    Si("Si",28.085),
    P("P",30.973761),
    S("S",32.065),
    Cl("Cl",35.453),
    K("K",39.0983),
    Ca("Ca",40.078),
    Cr("Cr",51.9961),
    Mn("Mn",54.938045),
    Fe("Fe",55.845),
    Ni("Ni",58.6934),
    Co("Co",58.933195),
    Cu("Cu",63.546),
    Zn("Zn",65.409),
    As("As",74.9215942),
    Se("Se",78.96),
    Br("Br",79.904),
    Mo("Mo",95.94),
    Ru("Ru",101.07),
    Pd("Pd",106.42),
    Ag("Ag",107.8682),
    Cd("Cd",112.411),
    I("I",126.90447),
    Pt("Pt",195.084),
    Au("Au",196.96655),
    Hg("Hg",200.59);

    private final String _title;
    private final String _symbol;
    private final double _avgMass;

    private static Map<String, ChemElement> titleMap = new HashMap<>();
    private static Map<String, ChemElement> symbolMap = new HashMap<>();
    static
    {
        for (ChemElement el: ChemElement.values())
        {
            symbolMap.put(el.getSymbol(), el);
            titleMap.put(el.getTitle(), el);
        }
    }

    public static ChemElement getElement(String title)
    {
        return titleMap.get(title);
    }

    public static ChemElement getElementForSymbol(String symbol)
    {
        return symbolMap.get(symbol);
    }

    ChemElement(String title, double avgMass)
    {
        this(title, title, avgMass);
    }
    ChemElement(String title, String symbol, double avgMass)
    {
        _title = title;
        _symbol = symbol;
        _avgMass = avgMass;
    }

    public String getTitle()
    {
        return _title;
    }

    public String getSymbol()
    {
        return _symbol;
    }
}
