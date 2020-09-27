<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <phase id="phase">
    <active pattern="is-valid"/>
  </phase>
  <pattern>
    <rule context="/">
      <assert test="false()"/>
    </rule>
  </pattern>
  <pattern id="is-valid">
    <rule context="/">
      <assert test="true()"/>
    </rule>
  </pattern>
</schema>
