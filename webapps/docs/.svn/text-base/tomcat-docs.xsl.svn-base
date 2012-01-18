<?xml version="1.0" encoding="ISO-8859-1"?>
<!-- Content Stylesheet for "docs" Documentation -->

<!-- $Id$ -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">


  <!-- Output method -->
  <xsl:output method="html"
            encoding="iso-8859-1"
              indent="no"/>


  <!-- Defined parameters (overrideable) -->
  <xsl:param    name="home-name"        select="'The JBoss Web Project'"/>
  <xsl:param    name="home-href"        select="'http://labs.jboss.com/jbossweb/'"/>
  <xsl:param    name="home-logo"        select="'/images/jboss_logo.gif'"/>
  <xsl:param    name="printer-logo"     select="'/images/printer.gif'"/>
  <xsl:param    name="jbossorg-logo"    select="'/images/jbossorg_logo.gif'"/>
  <xsl:param    name="jbossweb-logo"    select="'/images/jbossweblogo.gif'"/>
  <xsl:param    name="hdr_hdrtitle"     select="'/images/hdr_hdrtitle.gif'"/>
  <xsl:param    name="hdr_jbosslogo"    select="'/images/hdr_jbosslogo.gif'"/>
  <xsl:param    name="hdr_jbossorglogo" select="'/images/hdr_jbossorglogo.gif'"/>
  <xsl:param    name="relative-path"    select="'.'"/>
  <xsl:param    name="void-image"       select="'/images/void.gif'"/>
  <xsl:param    name="project-menu"     select="'menu'"/>
  <xsl:param    name="bodyonly"         select="'false'"/>
  <xsl:param    name="usehead"          select="'false'"/>
  <xsl:param    name="standalone"       select="''"/>
  <xsl:param    name="buglink"          select="'http://issues.apache.org/bugzilla/show_bug.cgi?id='"/>
  <xsl:param    name="jiralink"         select="'http://issues.jboss.org/browse/JBWEB-'"/>
  <xsl:param    name="jbossjiralink"    select="'http://issues.jboss.org/browse/'"/>

  <!-- Defined variables (non-overrideable) -->
  <xsl:variable name="body-bg"          select="'#ffffff'"/>
  <xsl:variable name="body-fg"          select="'#000000'"/>
  <xsl:variable name="body-link"        select="'#525D76'"/>
  <xsl:variable name="banner-bg"        select="'#eaeff2'"/>
  <xsl:variable name="banner-fg"        select="'#ffffff'"/>
  <xsl:variable name="sub-banner-bg"    select="'#eaeff2'"/>
  <xsl:variable name="sub-banner-fg"    select="'#ffffff'"/>
  <xsl:variable name="source-color"     select="'#023264'"/>
  <xsl:variable name="attributes-color" select="'#023264'"/>
  <xsl:variable name="table-th-bg"      select="'#039acc'"/>
  <xsl:variable name="table-td-bg"      select="'#a0ddf0'"/>

  <!-- Process an entire document into an HTML page -->
  <xsl:template match="document">
  <xsl:variable name="project"
              select="document('project.xml')/project"/>

    <xsl:if test="$bodyonly = 'true'">
      <xsl:apply-templates select="body/section"/>
    </xsl:if>
    
    <xsl:if test="$bodyonly != 'true'">

    <html xmlns="http://www.w3.org/1999/xhtml">
    <head>
    <title><xsl:value-of select="project/title"/> - <xsl:value-of select="properties/title"/></title>
    <xsl:variable name="csshref"><xsl:value-of select="$relative-path"/>/jbossweb.css</xsl:variable>
    <link href="{$csshref}" rel="stylesheet" type="text/css" />
    <xsl:for-each select="properties/author">
      <xsl:variable name="name">
        <xsl:value-of select="."/>
      </xsl:variable>
      <xsl:variable name="email">
        <xsl:value-of select="@email"/>
      </xsl:variable>
      <meta name="author" value="{$name}"/>
      <meta name="email" value="{$email}"/>
    </xsl:for-each>
    </head>

    <body>

    <div class="wrapper">

       <xsl:comment>HEADER</xsl:comment>
       <div class="header">
         <div class="floatleft"><a href="index.html">
           <xsl:variable name="src"><xsl:value-of select="$relative-path"/><xsl:value-of select="$hdr_hdrtitle"/></xsl:variable>
           <img src="{$src}" border="0"/></a></div>
         <div class="floatright"><a href="http://www.jboss.com/">
           <xsl:variable name="src"><xsl:value-of select="$relative-path"/><xsl:value-of select="$hdr_jbosslogo"/></xsl:variable>
           <img src="{$src}" alt="JBoss, a division of Red Hat" border="0"/></a><a href="http://www.jboss.org">
           <xsl:variable name="src"><xsl:value-of select="$relative-path"/><xsl:value-of select="$hdr_jbossorglogo"/></xsl:variable>
           <img src="{$src}" alt="JBoss.org - Community driven." border="0" /></a></div>
       </div>
       <div class="container">

       <xsl:comment>OPTIONAL MENU</xsl:comment>
       
       <xsl:if test="$project-menu = 'menu'">
         <div class="leftcol">
           <dl>
             <xsl:apply-templates select="project/body/menu"/>
           </dl>
         </div>
       
       <xsl:comment>MAIN</xsl:comment>
       <div class="maincol documentation">
          
         <!-- Add the printer friendly link for docs with a menu -->
         <xsl:if test="$project-menu = 'menu'">
           <xsl:variable name="src">
             <xsl:value-of select="$relative-path"/><xsl:value-of select="$printer-logo"/>
           </xsl:variable>
           <xsl:variable name="url">
             <xsl:value-of select="/document/@url"/>
           </xsl:variable>
           <div style="float:right; "><a href="printer/{$url}"><img src="{$src}" alt="Printer Friendly Version" border="0" /></a></div>
         </xsl:if>

         <h1><xsl:value-of select="properties/title"/></h1>
          
         <xsl:apply-templates select="body/section"/>
       
       </div>
       </xsl:if>
       
       <xsl:if test="$project-menu != 'menu'">
          
       <div class="maincolprint documentation">
           <h1><xsl:value-of select="properties/title"/></h1>
           <xsl:apply-templates select="body/section"/>
       </div>
       
       </xsl:if>
       
       </div>
       
       <xsl:comment>FOOTER</xsl:comment>
       <div class="footer">&#169; 1999-2007, Apache Software Foundation. &#169; 2007-2011 Red Hat Middleware, LLC. All Rights Reserved. </div>
       
    </div>

    </body>
    </html>
    </xsl:if>

  </xsl:template>


  <!-- Process a menu for the navigation bar -->
  <xsl:template match="menu">
    <dt><xsl:value-of select="@name"/></dt>
    <xsl:apply-templates select="item"/>
  </xsl:template>


  <!-- Process a menu item for the navigation bar -->
  <xsl:template match="item">
    <xsl:variable name="href">
      <xsl:value-of select="@href"/>
    </xsl:variable>
    <dd><a href="{$href}"><xsl:value-of select="@name"/></a></dd>
  </xsl:template>


  <!-- Process a documentation section -->
  <xsl:template match="section">

    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <h2><a name="@name"><xsl:value-of select="@name"/></a></h2>
    <xsl:apply-templates/>

  </xsl:template>


  <!-- Process a documentation subsection -->
  <xsl:template match="subsection">

    <blockquote>
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <h3><a name="@name"><xsl:value-of select="@name"/></a></h3>
    <xsl:apply-templates/>
    </blockquote>
    
  </xsl:template>

  <!-- Process a source code example -->
  <xsl:template match="source">
    <pre><xsl:value-of select="."/></pre>
  </xsl:template>

  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="attributes">
    <table width="100%" cellspacing="0" class="tableStyle">
      <tbody>
        <tr class="UnsortableTableHeader">
          <td>Attribute</td>
          <td>Description</td>
        </tr>

      <xsl:for-each select="attribute">
        <tr class="oddRow">
          <td class="first">
            <xsl:if test="@required = 'true'">
              <strong><code><xsl:value-of select="@name"/></code></strong>
            </xsl:if>
            <xsl:if test="@required != 'true'">
              <code><xsl:value-of select="@name"/></code>
            </xsl:if>
          </td>
          <td><xsl:apply-templates/></td>
        </tr>
      </xsl:for-each>

      </tbody>
    </table>
  </xsl:template>

  <!-- Process a properties list with nested property elements -->
  <xsl:template match="properties">
    <table width="100%" cellspacing="0" class="tableStyle">
      <tbody>
        <tr class="UnsortableTableHeader">
          <td>Attribute</td>
          <td>Description</td>
        </tr>

      <xsl:for-each select="property">
        <tr class="oddRow">
          <td class="first"><code><xsl:value-of select="@name"/></code></td>
          <td><xsl:apply-templates/></td>
        </tr>
      </xsl:for-each>

      </tbody>
    </table>
  </xsl:template>

  <!-- Fix relative links in printer friendly versions of the docs -->
  <xsl:template match="a">
    <xsl:variable name="href" select="@href"/>
    <xsl:choose>
      <xsl:when test="$standalone = 'standalone'">
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and starts-with(@href,'../')">
        <a href="../{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and starts-with(@href,'./') and contains(substring(@href,3),'/')">
        <a href=".{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and not(contains(@href,'//')) and not(starts-with(@href,'/')) and not(starts-with(@href,'#')) and contains(@href,'/')">
        <a href="../{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$href != ''">
        <a href="{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="name" select="@name"/>
        <a name="{$name}"><xsl:apply-templates/></a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Changelog related tags -->
  <xsl:template match="changelog">
    <table border="0" cellpadding="2" cellspacing="2">
      <xsl:apply-templates/>
    </table>
  </xsl:template>

  <xsl:template match="changelog/add">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/add.gif</xsl:variable>
      <td><img alt="add" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/update">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/update.gif</xsl:variable>
      <td><img alt="update" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/design">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/design.gif</xsl:variable>
      <td><img alt="design" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/docs">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/docs.gif</xsl:variable>
      <td><img alt="docs" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/fix">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/fix.gif</xsl:variable>
      <td><img alt="fix" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/scode">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/code.gif</xsl:variable>
      <td><img alt="code" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="status">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Priority</font>
        </th>
        <th width="50%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Action Item</font>
        </th>
        <th width="25%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Volunteers</font>
        </th>
        <xsl:for-each select="item">
        <tr>
          <td align="left" valign="center">
            <xsl:value-of select="@priority"/>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
          <td align="left" valign="center">
            <xsl:value-of select="@owner"/>
          </td>
        </tr>
        </xsl:for-each>
      </tr>
    </table>
  </xsl:template>

  <!-- Link to a bug report -->
  <xsl:template match="bug">
      <xsl:variable name="link"><xsl:value-of select="$buglink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}"><xsl:apply-templates/></a>
  </xsl:template>

  <!-- Link to a JIRA report -->
  <xsl:template match="jira">
      <xsl:variable name="link"><xsl:value-of select="$jiralink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}">JBWEB-<xsl:apply-templates/></a>
  </xsl:template>

  <!-- Link to a JBoss JIRA report for another project -->
  <xsl:template match="jboss-jira">
      <xsl:variable name="link"><xsl:value-of select="$jbossjiralink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}"><xsl:apply-templates/></a>
  </xsl:template>

  <!-- Process everything else by just passing it through -->
  <xsl:template match="*|@*">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
