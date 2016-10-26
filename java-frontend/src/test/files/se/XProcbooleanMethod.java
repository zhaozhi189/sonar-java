class XProcbooleanMethod {
  final boolean instanceOf(Object o) {
    return o instanceof String;
  }

  void testInstanceOf() {
    Object o = null;
    if (instanceOf(o)) {
      o.toString(); // compliant, o cannot be null here.
    }
  }
}
