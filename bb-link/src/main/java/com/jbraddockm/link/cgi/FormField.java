package com.jbraddockm.link.cgi;

/** The names of the form fields that requests carry. */
public enum FormField implements WireValue {

  /** Asks for the dynamic properties; its value repeats its own name. */
  GET_DYNAMIC_PROPERTIES("Get Dynamic Properties"),

  /** The action to perform. */
  ACTION("action"),

  /** A property name. */
  NAME("name"),

  /** A property value. */
  VALUE("value"),

  /** A wipe-endpoint command such as reading the PIN. */
  WIPE("wipe");

  private final String wireName;

  FormField(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
