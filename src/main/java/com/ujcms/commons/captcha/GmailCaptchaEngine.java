package com.ujcms.commons.captcha;

import com.jhlabs.image.PinchFilter;
import com.jhlabs.math.ImageFunction2D;
import com.octo.captcha.component.image.backgroundgenerator.BackgroundGenerator;
import com.octo.captcha.component.image.backgroundgenerator.UniColorBackgroundGenerator;
import com.octo.captcha.component.image.color.ColorGenerator;
import com.octo.captcha.component.image.color.RandomListColorGenerator;
import com.octo.captcha.component.image.deformation.ImageDeformation;
import com.octo.captcha.component.image.deformation.ImageDeformationByBufferedImageOp;
import com.octo.captcha.component.image.fontgenerator.FontGenerator;
import com.octo.captcha.component.image.fontgenerator.RandomFontGenerator;
import com.octo.captcha.component.image.textpaster.GlyphsPaster;
import com.octo.captcha.component.image.textpaster.TextPaster;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.GlyphsVisitors;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.OverlapGlyphsUsingShapeVisitor;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.RotateGlyphsRandomVisitor;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.ShearGlyphsRandomVisitor;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.TranslateAllToRandomPointVisitor;
import com.octo.captcha.component.image.textpaster.glyphsvisitor.TranslateGlyphsVerticalRandomVisitor;
import com.octo.captcha.component.image.wordtoimage.DeformedComposedWordToImage;
import com.octo.captcha.component.word.wordgenerator.RandomWordGenerator;
import com.octo.captcha.component.word.wordgenerator.WordGenerator;
import com.octo.captcha.image.gimpy.GimpyFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 仿 Gmail 验证码引擎
 *
 * @author liufang
 */
public class GmailCaptchaEngine extends com.octo.captcha.engine.image.ImageCaptchaEngine {
    public GmailCaptchaEngine(WordGenerator wordGenerator, FontGenerator fontGenerator, ColorGenerator colorGenerator,
                              BackgroundGenerator background, int minWordLength, int maxWordLength, float radius) {
        TextPaster randomPaster = new GlyphsPaster(minWordLength, maxWordLength, colorGenerator,
                new GlyphsVisitors[]{
                        // 垂直位置 2
                        new TranslateGlyphsVerticalRandomVisitor(2),
                        // 黏连程度 4
                        new OverlapGlyphsUsingShapeVisitor(4),
                        new TranslateAllToRandomPointVisitor(),
                        // 扭曲 .25 .25
                        new ShearGlyphsRandomVisitor(.25, .25),
                        // 旋转 Math.PI / 128
                        new RotateGlyphsRandomVisitor(Math.PI / 128),
                });

        PinchFilter pinch = new PinchFilter();

        pinch.setAmount(-.5f);
        pinch.setRadius(radius);
        pinch.setAngle((float) (Math.PI / 16));
        pinch.setCentreX(0.5f);
        pinch.setCentreY(-0.01f);
        pinch.setEdgeAction(ImageFunction2D.CLAMP);

        PinchFilter pinch2 = new PinchFilter();
        pinch2.setAmount(-.6f);
        pinch2.setRadius(radius);
        pinch2.setAngle((float) (Math.PI / 16));
        pinch2.setCentreX(0.3f);
        pinch2.setCentreY(1.01f);
        pinch2.setEdgeAction(ImageFunction2D.CLAMP);

        PinchFilter pinch3 = new PinchFilter();
        pinch3.setAmount(-.6f);
        pinch3.setRadius(radius);
        pinch3.setAngle((float) (Math.PI / 16));
        pinch3.setCentreX(0.8f);
        pinch3.setCentreY(-0.01f);
        pinch3.setEdgeAction(ImageFunction2D.CLAMP);

        List<ImageDeformation> textDef = new ArrayList<>();
        textDef.add(new ImageDeformationByBufferedImageOp(pinch));
        textDef.add(new ImageDeformationByBufferedImageOp(pinch2));
        textDef.add(new ImageDeformationByBufferedImageOp(pinch3));

        com.octo.captcha.component.image.wordtoimage.WordToImage word2image;
        word2image = new DeformedComposedWordToImage(false, fontGenerator, background,
                randomPaster, new ArrayList<>(),
                new ArrayList<>(), textDef);
        //noinspection unchecked
        factories.add(new GimpyFactory(wordGenerator, word2image, false));
    }

    public GmailCaptchaEngine() {
        this(new RandomWordGenerator("ABCDEGHJKLMNRSTUWXY235689"),
                new RandomFontGenerator(40, 40, new Font[]{
                        new Font("nyala", Font.BOLD, 40),
                        new Font("Bell MT", Font.PLAIN, 40),
                        new Font("Credit valley", Font.BOLD, 40)}, false),
                new RandomListColorGenerator(new Color[]{
                        new Color(23, 170, 27), new Color(220, 34, 11),
                        new Color(23, 67, 172)}),
                new UniColorBackgroundGenerator(150, 40, Color.white), 5, 6, 30);
    }
}