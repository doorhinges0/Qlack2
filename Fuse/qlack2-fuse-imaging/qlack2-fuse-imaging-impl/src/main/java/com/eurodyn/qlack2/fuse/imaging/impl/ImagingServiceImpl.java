package com.eurodyn.qlack2.fuse.imaging.impl;

import com.eurodyn.qlack2.fuse.imaging.api.ImagingService;
import com.eurodyn.qlack2.fuse.imaging.api.dto.ImageFormatHandler;
import com.eurodyn.qlack2.fuse.imaging.api.dto.ImageInfo;
import com.eurodyn.qlack2.fuse.imaging.api.exception.QImagingException;
import com.eurodyn.qlack2.fuse.imaging.api.util.ColorSpaceType;
import com.eurodyn.qlack2.fuse.imaging.api.util.ICCProfile;
import com.eurodyn.qlack2.fuse.imaging.api.util.ResamplingAlgorithm;
import com.eurodyn.qlack2.fuse.imaging.api.util.TIFFCompression;
import com.eurodyn.qlack2.fuse.imaging.impl.util.ImagingUtil;
import com.twelvemonkeys.image.ResampleOp;
import javax.annotation.PostConstruct;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@OsgiServiceProvider(classes = ImagingService.class)
public class ImagingServiceImpl implements ImagingService {
  // JUL reference.
  private static final Logger LOGGER = Logger.getLogger(ImagingServiceImpl.class.getName());

  // A reference to the bundle context to be able to read resources from classpath.
  @Inject
  BundleContext bundleContext;

  /**
   * Resamples an image to the new dimensions using one of the available resampling algorithms.
   *
   * @param originalImage The image to resample.
   * @param width The new widht.
   * @param height The new height.
   * @param resamplingAlgorithm The resampling algorithm to use.
   * @param imageType The type of the image (so that the resulting image is of the same type).
   * @return Returns a resampled image.
   */
  private byte[] resample(BufferedImage originalImage, int width, int height,
    ResamplingAlgorithm resamplingAlgorithm, String imageType) throws IOException {

    try (ByteArrayOutputStream resampledImageOutputStream = new ByteArrayOutputStream()) {
      BufferedImageOp resampler = new ResampleOp(width, height, resamplingAlgorithm.getVal());
      ImageIO.write(resampler.filter(originalImage, null), imageType, resampledImageOutputStream);
      resampledImageOutputStream.flush();
      return resampledImageOutputStream.toByteArray();
    }
  }

  /**
   * Initialiser in which all SPI readers/writers are registered with ImageIO.
   */
  @PostConstruct
  public void init() {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    registry.registerServiceProviders(ServiceRegistry.lookupProviders(ImageReaderSpi.class));
    registry.registerServiceProviders(ServiceRegistry.lookupProviders(ImageWriterSpi.class));
  }

  @Override
  public List<ImageFormatHandler> getSupportedReadFormats() {
    List<ImageFormatHandler> handlers = new ArrayList<>();

    for (String reader : ImageIO.getReaderFormatNames()) {
      final ImageFormatHandler imageFormatHandler = new ImageFormatHandler();
      imageFormatHandler.setFormat(reader);
      Iterator<ImageReader> imageReaders = ImageIO.getImageReadersByFormatName(reader);
      while (imageReaders.hasNext()) {
        final ImageReader next = imageReaders.next();
        imageFormatHandler.addHandlerClass(next.toString());
      }
      handlers.add(imageFormatHandler);
    }

    return handlers;
  }

  @Override
  public List<ImageFormatHandler> getSupportedWriteFormats() {
    List<ImageFormatHandler> handlers = new ArrayList<>();

    for (String reader : ImageIO.getWriterFormatNames()) {
      final ImageFormatHandler imageFormatHandler = new ImageFormatHandler();
      imageFormatHandler.setFormat(reader);
      Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByFormatName(reader);
      while (imageWriters.hasNext()) {
        final ImageWriter next = imageWriters.next();
        imageFormatHandler.addHandlerClass(next.toString());
      }
      handlers.add(imageFormatHandler);
    }

    return handlers;
  }

  @Override
  public boolean isFormatSupportedForRead(String format) {
    return getSupportedReadFormats().stream().anyMatch(o -> o.getFormat().equals(format));
  }

  @Override
  public boolean isFormatSupportedForWrite(String format) {
    return getSupportedWriteFormats().stream().anyMatch(o -> o.getFormat().equals(format));
  }

  @Override
  public ImageInfo getInfo(byte[] image) {
    ImageInfo imageInfo = null;

    try {
      imageInfo = new ImageInfo();
      try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
        BufferedImage bufferedImage = ImageIO.read(originalImageInputStream);
        imageInfo.setBitsPerPixel(bufferedImage.getColorModel().getPixelSize());
        imageInfo.setColorType(
          ColorSpaceType.valueOf(
            ColorSpaceType.getReverseVal(bufferedImage.getColorModel().getColorSpace().getType())));
        imageInfo.setHeight(bufferedImage.getHeight());
        imageInfo.setWidth(bufferedImage.getWidth());
        try (InputStream originalImageInputStream2 = new ByteArrayInputStream(image)) {
          imageInfo.setMimeType(
            new TikaConfig().getDetector().detect(originalImageInputStream2, new Metadata())
              .toString());
        }
        imageInfo.setDotsPerInch(ImagingUtil.getDPI(image));
        imageInfo.setFormat(ImagingUtil.getType(image));
      }
    } catch (IOException | TikaException e) {
      LOGGER.log(Level.SEVERE, "Could not obtain image info.", e);
      throw new QImagingException("Could not obtain image info.", e);
    }

    return imageInfo;
  }

  @Override
  public byte[] convert(byte[] image, String dstFormat) {
    return convert(image, dstFormat, null);
  }

  @Override
  public byte[] removeAlphaChannel(byte[] image) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      final String type = ImagingUtil.getType(image);
      try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
        BufferedImage originalImage = ImageIO.read(originalImageInputStream);
        BufferedImage newImage = new BufferedImage(originalImage.getWidth(),
          originalImage.getHeight(),
          BufferedImage.TYPE_INT_RGB);
        newImage.createGraphics().drawImage(originalImage, 0, 0, Color.BLACK, null);
        ImageIO.write(newImage, type, baos);
      }
      // Return image.
      return baos.toByteArray();
    } catch (Exception e) {
      throw new QImagingException("Could not remove alpha channel.", e);
    }
  }

  @Override
  public byte[] convert(byte[] image, String dstFormat, ICCProfile dstColorspace) {
    try (ByteArrayOutputStream dstImage = new ByteArrayOutputStream()) {
      // Read image.
      try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
        BufferedImage originalImage = ImageIO.read(originalImageInputStream);

        // Convert colospace if requested.
        if (dstColorspace != null) {
          String iccProfileFile = "icc/" + dstColorspace.name() + ".icc";
          ColorSpace cmykColorSpace = new ICC_ColorSpace(ICC_Profile.getInstance(
            bundleContext.getBundle().getResource(iccProfileFile).openStream()));
          ColorConvertOp op = new ColorConvertOp(originalImage.getColorModel().getColorSpace(),
            cmykColorSpace, null);
          originalImage = op.filter(originalImage, null);
        }

        // Write destination image.
        if (!ImageIO.write(originalImage, dstFormat, dstImage)) {
          throw new QImagingException(MessageFormat.format(
            "Could not write destination format: {0}", dstFormat));
        }
      }
      // Return image.
      return dstImage.toByteArray();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not convert image.", e);
      throw new QImagingException("Could not convert image.", e);
    }
  }

  @Override
  public byte[] convertToTIFF(byte[] image, TIFFCompression tiffCompression) {
    return convertToTIFF(image, null, tiffCompression);
  }

  @Override
  public byte[] convertToTIFF(byte[] image, ICCProfile dstColorspace,
    TIFFCompression tiffCompression) {
    try (ByteArrayOutputStream convertedImage = new ByteArrayOutputStream()) {
      // Read image.
      try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
        BufferedImage originalImage = ImageIO.read(originalImageInputStream);

        // Convert colorspace.
        if (dstColorspace != null) {
          String iccProfileFile = "icc/" + dstColorspace.name() + ".icc";
          ColorSpace cmykColorSpace = new ICC_ColorSpace(ICC_Profile.getInstance(
            bundleContext.getBundle().getResource(iccProfileFile).openStream()));
          ColorConvertOp op = new ColorConvertOp(originalImage.getColorModel().getColorSpace(),
            cmykColorSpace, null);
          originalImage = op.filter(originalImage, null);
        }

        // Compress.
        final ImageWriteParam params = ImageIO.getImageWritersByFormatName("TIFF").next()
          .getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionType(tiffCompression.getVal());
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(convertedImage)) {
          ImageWriter writer = ImageIO.getImageWritersByFormatName("TIFF").next();
          writer.setOutput(ios);
          writer.write(null, new IIOImage(originalImage, null, null), params);
        }
      }
      return convertedImage.toByteArray();
    } catch (IOException e) {
      throw new QImagingException("Could not convert image.", e);
    }
  }

  @Override
  public byte[] resampleByPercent(byte[] image, int percent,
    ResamplingAlgorithm resamplingAlgorithm) {
    try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
      BufferedImage originalBufferedImage = ImageIO.read(originalImageInputStream);
      return resample(originalBufferedImage,
        (int) (originalBufferedImage.getWidth() * ((float) percent / 100f)),
        (int) (originalBufferedImage.getHeight() * ((float) percent / 100f)), resamplingAlgorithm,
        ImagingUtil.getType(image));
    } catch (IOException e) {
      throw new QImagingException("Could not resample image by percent.", e);
    }
  }

  @Override
  public byte[] resampleByFactor(byte[] image, float factor,
    ResamplingAlgorithm resamplingAlgorithm) {
    try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
      BufferedImage originalBufferedImage = ImageIO.read(originalImageInputStream);
      return resample(originalBufferedImage, (int) (originalBufferedImage.getWidth() * factor),
        (int) (originalBufferedImage.getHeight() * factor), resamplingAlgorithm,
        ImagingUtil.getType(image));
    } catch (IOException e) {
      throw new QImagingException("Could not resample image by factor.", e);
    }
  }

  @Override
  public byte[] resampleByWidth(byte[] image, int width, ResamplingAlgorithm resamplingAlgorithm) {
    try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
      BufferedImage originalBufferedImage = ImageIO.read(originalImageInputStream);
      float newYRatio = (float) width / (float) originalBufferedImage.getWidth();
      return resample(originalBufferedImage, width,
        (int) (originalBufferedImage.getHeight() * newYRatio), resamplingAlgorithm,
        ImagingUtil.getType(image));
    } catch (IOException e) {
      throw new QImagingException("Could not resample image by width.", e);
    }
  }

  @Override
  public byte[] resampleByHeight(byte[] image, int height,
    ResamplingAlgorithm resamplingAlgorithm) {
    try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
      BufferedImage originalBufferedImage = ImageIO.read(originalImageInputStream);
      float newXRatio = (float) height / (float) originalBufferedImage.getHeight();
      return resample(originalBufferedImage, (int) (originalBufferedImage.getWidth() * newXRatio),
        height, resamplingAlgorithm, ImagingUtil.getType(image));
    } catch (IOException e) {
      throw new QImagingException("Could not resample image by height.", e);
    }
  }

  @Override
  public byte[] resample(byte[] image, int width, int height,
    ResamplingAlgorithm resamplingAlgorithm) {
    try (InputStream originalImageInputStream = new ByteArrayInputStream(image)) {
      BufferedImage originalBufferedImage = ImageIO.read(originalImageInputStream);
      return resample(originalBufferedImage, width, height, resamplingAlgorithm,
        ImagingUtil.getType(image));
    } catch (IOException e) {
      throw new QImagingException("Could not resample image.", e);
    }
  }
}
