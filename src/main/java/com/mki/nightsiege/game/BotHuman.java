package com.mki.nightsiege.game;

import java.util.function.ToDoubleBiFunction;

/**
 * The "human" switch of the team bots (Tor 6.0, default OFF so old runs stay comparable): sim token
 * {@code mensch=1}, every knob below as {@code mensch.<name>=<value>}. Every default is a STARTING value with
 * its reason; none is calibrated yet (that comes with the raw data of the real nights, tools/sim/eichung_vergleich.py).
 */
final class BotHuman {

    static final BotHuman OFF = new BotHuman(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    final boolean on;
    /** Standard deviation of each bot's skill around "koennen" (real nights fit alone 0.8, groups 0.6: players differ). */
    final double streuung;
    /** Aim error of bow and crossbow in degrees (Gaussian per shot): a hurried player misses by about a block at 20 blocks. */
    final double zielgrad;
    /** Chance a melee swing is wasted (too early or beside the target, the charge is lost). */
    final double nahfehler;
    /** Extra reaction time of abilities in ticks, random 0..value on top of the 1/skill delay (human hands, old controls). */
    final double spaet;
    /** Chance that a ready ability is not pressed at this check (forgotten or saved up). */
    final double vergessen;
    /** Life share below which melee roles step back to the core (players retreat when red). */
    final double rueckHp;
    /** How long the retreat lasts, in ticks; the next one cannot start for as long again. */
    final double rueckTicks;
    /** Life share below which a potion is drunk (the perfect bot drank at 45 % at once). */
    final double traenkeHp;
    /** Reaction time before the drink, in ticks (a player first has to see the hearts). */
    final double traenkeVerzug;
    /** Chance an archer takes the wall top instead of standing in front of the gate (players often prefer the front). */
    final double mauer;

    private BotHuman(boolean on, double streuung, double zielgrad, double nahfehler, double spaet, double vergessen,
                     double rueckHp, double rueckTicks, double traenkeHp, double traenkeVerzug, double mauer) {
        this.on = on;
        this.streuung = streuung;
        this.zielgrad = zielgrad;
        this.nahfehler = nahfehler;
        this.spaet = spaet;
        this.vergessen = vergessen;
        this.rueckHp = rueckHp;
        this.rueckTicks = rueckTicks;
        this.traenkeHp = traenkeHp;
        this.traenkeVerzug = traenkeVerzug;
        this.mauer = mauer;
    }

    /** {@code get} gives the value of a knob name (second argument = its default). */
    static BotHuman of(ToDoubleBiFunction<String, Double> get) {
        return new BotHuman(true, get.applyAsDouble("streuung", 0.15), get.applyAsDouble("zielgrad", 3.0),
                get.applyAsDouble("nahfehler", 0.15), get.applyAsDouble("spaet", 30.0), get.applyAsDouble("vergessen", 0.25),
                get.applyAsDouble("rueck_hp", 0.30), get.applyAsDouble("rueck_ticks", 60.0),
                get.applyAsDouble("traenke_hp", 0.40), get.applyAsDouble("traenke_verzug", 15.0),
                get.applyAsDouble("mauer", 0.6));
    }
}
