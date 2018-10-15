package com.bookmap.sergey.custommodules;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import com.bookmap.sergey.custommodules.guiutils.SettingsPanelBase;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.indicators.IndicatorColorInterface;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.Bar;
import velox.api.layer1.simplified.BarDataListener;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.CustomSettingsPanelProvider;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.TimeListener;
import velox.api.layer1.simplified.TradeDataListener;
import velox.colors.ColorsChangedListener;
import velox.gui.StrategyPanel;
import velox.gui.colors.ColorsConfigItem;

@Layer1SimpleAttachable
@Layer1StrategyName("ATR Trailing Stop")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION1)
public class AtrTrailingStop
        implements CustomModule, TradeDataListener, BarDataListener, TimeListener, CustomSettingsPanelProvider {

    private static enum Trend {
        Up, Down, Undefined
    }

    private Indicator lineBuy;
    private Indicator lineSell;

    private double buyPrice = Double.NaN;
    private double sellPrice = Double.NaN;
    private double lastTradePrice = Double.NaN;

    private final static Color defaultColorBuy = Color.WHITE;
    private final static Color defaultColorSell = Color.WHITE;
    private final static double defaultMultiplier = 1.5;
    private final static double defaultTr = 5;
    private final static double defaultAtr = defaultTr;
    private final static long defaultBarPeriod = Intervals.INTERVAL_1_MINUTE;
    private final static int defaultAtrNumBars = 10;

    private static Color colorBuy = defaultColorBuy;
    private static Color colorSell = defaultColorSell;
    private static double multiplier = defaultMultiplier;
    private static double tr = defaultTr;
    private static double atr = defaultAtr;
    private static long barPeriod = defaultBarPeriod;
    private int atrNumBars = defaultAtrNumBars;

    private final JLabel labelTr = new JLabel();
    private final JLabel labelAtr = new JLabel();

    private long currentTimeNanoseconds;

    @Override
    public void initialize(String alias, InstrumentInfo info, Api api) {
        Log.info("initialize colors " + colorBuy + "; " + colorSell);
        lineBuy = api.registerIndicator("ATR TS Buy", GraphType.PRIMARY, colorBuy);
        lineSell = api.registerIndicator("ATR TS Sell", GraphType.PRIMARY, colorSell);
    }

    @Override
    public void onTimestamp(long t) {
        currentTimeNanoseconds = t;
    }

    @Override
    public long getBarInterval() {
        return barPeriod;
    }

    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        tr = multiplier * (bar.getHigh() - bar.getLow());
        atr = ((atrNumBars - 1) * atr + tr) / atrNumBars;
        onBarUpdate(bar);
    }

    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        double pricePips = fixPrice(price);
        if (lastTradePrice != pricePips) {
            lastTradePrice = pricePips;
            onPriceUpdate();
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public StrategyPanel[] getCustomSettingsPanels() {
        StrategyPanel p1 = getColorsSettingsPanel();
        StrategyPanel p2 = getOtherSettingsPanel();
        StrategyPanel p3 = getDisplayPanel();
        return new StrategyPanel[] { p1, p2, p3 };
    }

    private void onPriceUpdate() {
        Trend trend = !Double.isNaN(buyPrice) ? Trend.Down : (!Double.isNaN(sellPrice) ? Trend.Up : Trend.Undefined);
        double buyPriceDefault = fixPrice(lastTradePrice + atr);
        double sellPriceDefault = fixPrice(lastTradePrice - atr);
        switch (trend) {
        case Up:
            if (sellPrice < sellPriceDefault) {
                setSellPrice(sellPriceDefault);
            } else if (sellPrice > lastTradePrice) {
                setBuyPrice(buyPriceDefault);
            }
            break;
        case Down:
            if (buyPrice > buyPriceDefault) {
                setBuyPrice(buyPriceDefault);
            } else if (buyPrice < lastTradePrice) {
                setSellPrice(sellPriceDefault);
            }
            break;
        case Undefined:
            setBuyPrice(buyPriceDefault);
        }
    }

    private void onSettingsUpdate() {
        Trend trend = !Double.isNaN(buyPrice) ? Trend.Down : (!Double.isNaN(sellPrice) ? Trend.Up : Trend.Undefined);
        switch (trend) {
        case Up:
            double sellPriceDefault = fixPrice(lastTradePrice - atr);
            setSellPrice(sellPriceDefault);
            break;
        case Down:
        case Undefined:
            double buyPriceDefault = fixPrice(lastTradePrice + atr);
            setBuyPrice(buyPriceDefault);
        }
        onAtrUpdate();
    }

    private double fixPrice(double price) {
        return Double.isNaN(price) ? price : Math.round(price);
    }

    private void setBuyPrice(double price) {
        buyPrice = price;
        lineBuy.addPoint(price);
        if (!Double.isNaN(sellPrice)) {
            sellPrice = Double.NaN;
            lineSell.addPoint(Double.NaN);
        }
    }

    private void setSellPrice(double price) {
        sellPrice = price;
        lineSell.addPoint(price);
        if (!Double.isNaN(buyPrice)) {
            buyPrice = Double.NaN;
            lineBuy.addPoint(Double.NaN);
        }
    }

    private void updateColor(boolean isBuy, Color color) {
        if (isBuy) {
            colorBuy = color;
        } else {
            colorSell = color;
        }
    }

    private StrategyPanel getColorsSettingsPanel() {
        SettingsPanelBase panel = new SettingsPanelBase("Colors");
        panel.add(new ColorsConfigItem("Color of Buy Trailing Stop:", defaultColorBuy, buildColorInterface(true),
                () -> Log.info("Colors were changed")));
        panel.add(new ColorsConfigItem("Color of Sell Trailing Stop:", defaultColorSell, buildColorInterface(false),
                () -> Log.info("Colors were changed")));
        return panel;
    }

    private StrategyPanel getOtherSettingsPanel() {
        SettingsPanelBase panel = new SettingsPanelBase("Settings");
        addMultiplierSettings(panel);
        addBarPeriodSettings(panel);
        addAtrNumBars(panel);
        return panel;
    }

    private void addMultiplierSettings(final SettingsPanelBase panel) {
        JComboBox<Double> c = new JComboBox<>(new Double[] { 0.5, 0.75, 1.0, 1.5, 2.0, 3.5, 5.0, 7.5, 10.0 });
        c.setSelectedItem(multiplier);
        c.setEditable(true);
        c.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                double newMultiplier = (double) c.getSelectedItem();
                if (newMultiplier != multiplier) {
                    atr = atr * newMultiplier / multiplier;
                    multiplier = newMultiplier;
                    onSettingsUpdate();
                }
            }
        });
        panel.addSettingsRow("Multiplier:", c);
    }

    private void addBarPeriodSettings(final SettingsPanelBase panel) {
        JComboBox<Integer> c = new JComboBox<>(
                new Integer[] { 1, 2, 3, 5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1800, 3600 });
        int selected = (int) (barPeriod / 1_000_000_000L);
        c.setSelectedItem(selected);
        c.setEditable(true);
        c.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                long newBarPeriod = 1_000_000_000L * (int) c.getSelectedItem();
                if (newBarPeriod != barPeriod) {
                    barPeriod = newBarPeriod;
                    onSettingsUpdate();
                }
            }
        });
        panel.addSettingsRow("Bar period [seconds]:", c);
    }

    private void addAtrNumBars(final SettingsPanelBase panel) {
        JComboBox<Integer> c = new JComboBox<>(new Integer[] { 5, 10, 20, 50, 100 });
        c.setSelectedItem(atrNumBars);
        c.setEditable(true);
        c.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                int newAtrNumBars = (int) c.getSelectedItem();
                if (newAtrNumBars != atrNumBars) {
                    atrNumBars = newAtrNumBars;
                    onSettingsUpdate();
                }
            }
        });
        panel.addSettingsRow("Number of Bars:", c);
    }

    private SettingsPanelBase getDisplayPanel() {
        SettingsPanelBase panel = new SettingsPanelBase("Current values");
        onAtrUpdate();
        panel.addSettingsRow("Last True Range (multiplied) [ticks]:", labelTr);
        panel.addSettingsRow("Average True Range [ticks]:", labelAtr);
        return panel;
    }

    private void onBarUpdate(Bar bar) {
        String t = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS").format(new Date(currentTimeNanoseconds / 1_000_000L));
        String info = String.format("onBar time: %s. OHLC: %.0f, %.0f, %.0f, %.0f. Volume Buy Sell: %d, %d", t,
                bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), bar.getVolumeBuy(), bar.getVolumeSell());
        Log.info(info);
        onAtrUpdate();
    }

    private void onAtrUpdate() {
        labelTr.setText(String.format("%.3f", tr));
        labelAtr.setText(String.format("%.3f", atr));
    }

    private IndicatorColorInterface buildColorInterface(boolean isBuy) {
        IndicatorColorInterface colorInterface = new IndicatorColorInterface() {

            @Override
            public void set(String name, Color color) {
                updateColor(isBuy, color);
                Log.info("set color " + color);
            }

            @Override
            public Color getOrDefault(String name, Color defaultValue) {
                Log.info("get color ");
                return isBuy ? colorBuy : colorSell;
            }

            @Override
            public void addColorChangeListener(ColorsChangedListener listener) {
                // This isn't needed unless custom module itself changing the colors after the
                // panel is shown
            }
        };
        return colorInterface;
    }

}
