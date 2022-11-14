package com.github.bannmann.trako;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.github.bannmann.trako.core.Parser;
import com.github.bannmann.trako.core.ResourceModuleUriResolver;
import com.github.bannmann.trako.core.TextWidth;
import com.github.bannmann.trako.core.TrakoGenerator;
import com.github.bannmann.trako.core.XhtmlToZip;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmNode;

public class Trako
{
  private static final String COLOR_PATTERN = "#[0-9a-fA-F]{6}";
  private static final String INTEGER_PATTERN = "[0-9]+";
  private static final int DEFAULT_PORT = 8080;

  public static void main(String[] args) throws Exception
  {
    TrakoGenerator generator = new TrakoGenerator();
    boolean input = false;

    Charset charset = null;
    boolean errors = false;

    for (int i = 0; i < args.length; ++i)
    {
      String arg = args[i];
      if (arg.equals("-suppressebnf"))
      {
        generator.setShowEbnf(false);
      }
      else if (arg.startsWith("-width:"))
      {
        String substring = arg.substring(7);
        if (substring.matches(INTEGER_PATTERN))
        {
          generator.setWidth(Integer.parseInt(substring));
        }
        else
        {
          System.err.println("invalid width value");
          System.err.println();
          errors = true;
          break;
        }
      }
      else if (arg.startsWith("-color:"))
      {
        String substring = arg.substring(7);
        if (substring.matches(COLOR_PATTERN))
        {
          Color color = Color.decode("0x" + substring.substring(1));
          generator.setBaseColor(color);
        }
        else
        {
          System.err.println("invalid color code, color code must match " + COLOR_PATTERN);
          System.err.println();
          errors = true;
          break;
        }
      }
      else if (arg.startsWith("-offset:"))
      {
        String substring = arg.substring(8);
        if (substring.matches(INTEGER_PATTERN))
        {
          generator.setColorOffset(Integer.parseInt(substring));
        }
        else
        {
          System.err.println("invalid offset value");
          System.err.println();
          errors = true;
          break;
        }
      }
      else if (arg.startsWith("-padding:"))
      {
        String substring = arg.substring(9);
        if (substring.matches(INTEGER_PATTERN))
        {
          generator.setPadding(Integer.parseInt(substring));
        }
        else
        {
          System.err.println("invalid padding value");
          System.err.println();
          errors = true;
          break;
        }
      }
      else if (arg.startsWith("-strokewidth:"))
      {
        String substring = arg.substring(13);
        if (substring.matches(INTEGER_PATTERN))
        {
          generator.setStrokeWidth(Integer.parseInt(substring));
        }
        else
        {
          System.err.println("invalid stroke width value");
          System.err.println();
          errors = true;
          break;
        }
      }
      else if (arg.equals("-png"))
      {
        generator.setOutputType(TrakoGenerator.OutputType.HTML_PNG_ZIP);
      }
      else if (arg.equals("-md"))
      {
        generator.setOutputType(TrakoGenerator.OutputType.MARKDOWN_SVG);
      }
      else if (arg.startsWith("-out:"))
      {
        generator.setOutput(new FileOutputStream(arg.substring(5)));
      }
      else if (arg.equals("-keeprecursion"))
      {
        generator.setRecursionElimination(false);
      }
      else if (arg.equals("-nofactoring"))
      {
        generator.setFactoring(false);
      }
      else if (arg.equals("-noinline"))
      {
        generator.setInlineLiterals(false);
      }
      else if (arg.equals("-noepsilon"))
      {
        generator.setKeepEpsilon(false);
      }
      else if (arg.startsWith("-enc:"))
      {
        charset = Charset.forName(arg.substring(5));
      }
      else if (arg.equals("-"))
      {
        input = true;
      }
      else if (arg.startsWith("-"))
      {
        System.err.println("unsupported option: " + arg);
        System.err.println();
        errors = true;
        break;
      }
      else
      {
        System.setIn(new FileInputStream(arg));
        input = true;
      }

      if (input)
      {
        if (i + 1 != args.length)
        {
          System.err.println("excessive input file specification: " + args[i + 1]);
          System.err.println();
          errors = true;
        }
        break;
      }
    }

    if (errors || !input)
    {
      usage(System.err, determineJarName());
    }
    else
    {
      byte[] bytes = read(System.in);
      String grammar = decode(charset, bytes);
      generator.generate(grammar);
    }
  }

  private static String determineJarName() throws URISyntaxException
  {
    String jarPath = Trako.class
      .getProtectionDomain()
      .getCodeSource()
      .getLocation()
      .toURI()
      .getPath();
    return jarPath.substring(jarPath.lastIndexOf('/') + 1);
  }

  private static String decode(Charset charset, byte[] bytes)
  {
    if (charset == null)
    {
      return decode(bytes);
    }
    else
    {
      return decode(bytes, 0, charset);
    }
  }

  public static void usage(PrintStream out, final String jarName)
  {
    out.println("Trako");
    out.println("  version " + TrakoVersion.VERSION);
    out.println("  released " + TrakoVersion.DATE);
    out.println();
    out.println("Usage: java -jar " +
      jarName +
      " {-suppressebnf|-keeprecursion|-nofactoring|-noinline|-noepsilon|-color:COLOR|-offset:OFFSET|-png|-md|-out:FILE|width:PIXELS}... GRAMMAR");
    out.println();
    out.println("  -suppressebnf    do not show EBNF next to generated diagrams");
    out.println("  -keeprecursion   no direct recursion elimination");
    out.println("  -nofactoring     no left or right factoring");
    out.println("  -noinline        do not inline nonterminals that derive to single literals");
    out.println("  -noepsilon       remove nonterminal references that derive to epsilon only");
    out.println("  -color:COLOR     use COLOR as base color, pattern: " + COLOR_PATTERN);
    out.println("  -offset:OFFSET   hue offset to secondary color in degrees");
    out.println("  -png             create HTML+PNG in a ZIP file, rather than XHTML+SVG output");
    //  out.println("  -md              create Markdown with embedded SVG, rather than XHTML+SVG output");
    out.println("  -out:FILE        create FILE, rather than writing result to standard output");
    out.println("  -width:PIXELS    try to break graphics into multiple lines, when width exceeds PIXELS (default 992)");
    out.println("  -enc:ENCODING    set grammar input encoding (default: autodetect UTF8/16 or use system encoding)");
    out.println();
    out.println("  GRAMMAR          path of grammar, in W3C style EBNF (use '-' for stdin)");
  }

  private static byte[] read(InputStream input) throws Exception
  {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[32768];
    for (int length; (length = input.read(chunk)) != -1; )
      buffer.write(chunk, 0, length);
    return buffer.toByteArray();
  }

  private static String decode(byte[] bytes)
  {
    final byte[] UTF_8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    final byte[] UTF_16BE = { (byte) 0xFE, (byte) 0xFF };
    final byte[] UTF_16LE = { (byte) 0xFF, (byte) 0xFE };
    return startsWith(bytes, UTF_8)    ? decode(bytes,    UTF_8.length, StandardCharsets.UTF_8)
         : startsWith(bytes, UTF_16BE) ? decode(bytes, UTF_16BE.length, StandardCharsets.UTF_16BE)
         : startsWith(bytes, UTF_16LE) ? decode(bytes, UTF_16LE.length, StandardCharsets.UTF_16LE)
          : decode(bytes, 0, Charset.defaultCharset());
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix)
  {
    return Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
  }

  private static String decode(byte[] bytes, int offset, Charset charset)
  {
    return new String(bytes, offset, bytes.length - offset, charset);
  }
}
