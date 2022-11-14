package com.github.bannmann.trako;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.github.bannmann.trako.core.Parser;
import com.github.bannmann.trako.core.ResourceModuleUriResolver;
import com.github.bannmann.trako.core.TextWidth;
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
  private static final String TRAKO_URL = "https://bannmann.github.io/trako";
  private static final String COLOR_PATTERN = "#[0-9a-fA-F]{6}";
  private static final String INTEGER_PATTERN = "[0-9]+";
  private static final int DEFAULT_PORT = 8080;

  public static void main(String[] args) throws Exception
  {
    boolean input = false;
    OutputStream output = System.out;

    boolean zip = false;
    boolean markdown = false;
    boolean showEbnf = true;
    boolean factoring = true;
    boolean recursionElimination = true;
    boolean inline = true;
    boolean keep = true;
    String color = null;
    int spread = 0;
    Integer padding = null;
    Integer strokeWidth = null;
    Integer width = null;
    Charset charset = null;
    boolean errors = false;

    for (int i = 0; i < args.length; ++i)
    {
      String arg = args[i];
      if (arg.equals("-suppressebnf"))
      {
        showEbnf = false;
      }
      else if (arg.startsWith("-width:"))
      {
        String substring = arg.substring(7);
        if (substring.matches(INTEGER_PATTERN))
        {
          width = Integer.parseInt(substring);
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
          color = substring;
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
          spread = Integer.parseInt(substring);
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
          padding = Integer.parseInt(substring);
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
          strokeWidth = Integer.parseInt(substring);
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
        zip = true;
      }
      else if (arg.equals("-md"))
      {
        markdown = true;
      }
      else if (arg.startsWith("-out:"))
      {
        output = new FileOutputStream(arg.substring(5));
      }
      else if (arg.equals("-keeprecursion"))
      {
        recursionElimination = false;
      }
      else if (arg.equals("-nofactoring"))
      {
        factoring = false;
      }
      else if (arg.equals("-noinline"))
      {
        inline = false;
      }
      else if (arg.equals("-noepsilon"))
      {
        keep = false;
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
      usage(System.err, Trako.class.getName());
    }
    else
    {
      byte[] bytes = read(System.in);

      String ebnf;
      if (charset == null)
      {
        ebnf = decode(bytes);
      }
      else
      {
        ebnf = decode(bytes, 0, charset);
      }

      Configuration configuration = new Configuration();
      configuration.registerExtensionFunction(new Parser.SaxonDefinition_Grammar());
      Processor processor = new Processor(configuration);
      processor.setConfigurationProperty(Feature.XSD_VERSION, "1.1");
      new TextWidth.SaxonInitializer().initialize(processor.getUnderlyingConfiguration());

      XQueryCompiler compiler = processor.newXQueryCompiler();
      compiler.setModuleURIResolver(ResourceModuleUriResolver.instance);
      String query = "import module namespace i='com/github/bannmann/trako/xq/basic-interface.xq';\n" +
        "declare variable $ebnf external;\n" +
        "declare variable $show-ebnf external;\n" +
        "declare variable $recursion-elimination external;\n" +
        "declare variable $factoring external;\n" +
        "declare variable $inline external;\n" +
        "declare variable $keep external;\n" +
        "declare variable $width external;\n" +
        "declare variable $color external;\n" +
        "declare variable $spread external;\n" +
        "i:ebnf-to-xhtml($ebnf, $show-ebnf, $recursion-elimination, $factoring, $inline, $keep, $width, $color, $spread, '" +
        TRAKO_URL +
        "')";
      XQueryExecutable executable = compiler.compile(query);
      XQueryEvaluator xqueryEvaluator = executable.load();

      xqueryEvaluator.setExternalVariable(new QName("ebnf"), new XdmAtomicValue(ebnf));
      xqueryEvaluator.setExternalVariable(new QName("show-ebnf"), new XdmAtomicValue(showEbnf));
      xqueryEvaluator.setExternalVariable(new QName("recursion-elimination"), new XdmAtomicValue(recursionElimination));
      xqueryEvaluator.setExternalVariable(new QName("factoring"), new XdmAtomicValue(factoring));
      xqueryEvaluator.setExternalVariable(new QName("inline"), new XdmAtomicValue(inline));
      xqueryEvaluator.setExternalVariable(new QName("keep"), new XdmAtomicValue(keep));
      xqueryEvaluator.setExternalVariable(new QName("width"),
        width == null
          ? XdmEmptySequence.getInstance()
          : new XdmAtomicValue(width));
      xqueryEvaluator.setExternalVariable(new QName("color"),
        color == null
          ? XdmEmptySequence.getInstance()
          : new XdmAtomicValue(color));
      xqueryEvaluator.setExternalVariable(new QName("spread"), new XdmAtomicValue(spread));
      xqueryEvaluator.setExternalVariable(new QName("com/github/bannmann/trako/xq/ast-to-svg.xq", "version"), new XdmAtomicValue(TrakoVersion.VERSION));
      xqueryEvaluator.setExternalVariable(new QName("com/github/bannmann/trako/xq/ast-to-svg.xq", "java-version"), new XdmAtomicValue(javaVersion()));
      xqueryEvaluator.setExternalVariable(new QName("com/github/bannmann/trako/xq/ast-to-svg.xq", "date"), new XdmAtomicValue(TrakoVersion.DATE));
      if (padding != null)
      {
        xqueryEvaluator.setExternalVariable(new QName("com/github/bannmann/trako/xq/ast-to-svg.xq", "padding"), new XdmAtomicValue(padding));
      }
      if (strokeWidth != null)
      {
        xqueryEvaluator.setExternalVariable(new QName("com/github/bannmann/trako/xq/ast-to-svg.xq", "stroke-width"), new XdmAtomicValue(strokeWidth));
      }

      if (zip)
      {
        XdmNode node = (XdmNode) xqueryEvaluator.iterator()
          .next();
        new XhtmlToZip().convert(node.getUnderlyingNode(), output);
      }
      else if (markdown)
      {
        Serializer serializer = processor.newSerializer(output);
        serializer.setOutputProperty(Serializer.Property.METHOD, "text");
        serializer.setOutputProperty(Serializer.Property.ENCODING, StandardCharsets.UTF_8.name());
        XQueryEvaluator toMarkdown = compiler.compile("import module namespace m='com/github/bannmann/trako/xq/xhtml-to-md.xq';\n" +
            "declare variable $xhtml external;\n" +
            "m:transform($xhtml)")
          .load();
        toMarkdown.setExternalVariable(new QName("xhtml"),
          (XdmNode) xqueryEvaluator.iterator()
            .next());
        toMarkdown.run(processor.newSerializer(output));
      }
      else
      {
        Serializer serializer = processor.newSerializer(output);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xhtml");
        serializer.setOutputProperty(Serializer.Property.ENCODING, StandardCharsets.UTF_8.name());
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        serializer.setOutputProperty(Serializer.Property.VERSION, "1.0");
        serializer.setOutputProperty(Serializer.Property.DOCTYPE_SYSTEM, "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");
        serializer.setOutputProperty(Serializer.Property.DOCTYPE_PUBLIC, "-//W3C//DTD XHTML 1.0 Transitional//EN");
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
        xqueryEvaluator.run(serializer);
      }
    }
  }

  public static void usage(PrintStream out, final String file)
  {
    out.println("Trako");
    out.println("  version " + TrakoVersion.VERSION);
    out.println("  released " + TrakoVersion.DATE);
    out.println();
    out.println("Usage: java -jar " +
      file +
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
    {
      buffer.write(chunk, 0, length);
    }
    return buffer.toByteArray();
  }

  private static String decode(byte[] bytes)
  {
    final byte[] UTF_8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    final byte[] UTF_16BE = { (byte) 0xFE, (byte) 0xFF };
    final byte[] UTF_16LE = { (byte) 0xFF, (byte) 0xFE };
    return startsWith(bytes, UTF_8)
      ? decode(bytes, UTF_8.length, StandardCharsets.UTF_8)
      : startsWith(bytes, UTF_16BE)
        ? decode(bytes, UTF_16BE.length, StandardCharsets.UTF_16BE)
        : startsWith(bytes, UTF_16LE)
          ? decode(bytes, UTF_16LE.length, StandardCharsets.UTF_16LE)
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

  public static int javaVersion()
  {
    try (InputStream classFile = Trako.class.getClassLoader()
      .getResourceAsStream(Trako.class.getName()
        .replace('.', '/') + ".class"))
    {
      byte[] bytes = new byte[8];
      classFile.read(bytes);
      return bytes[7] - 44;
    }
    catch (IOException e)
    {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
