// jquery-validation 和 bootstrap4 整合
if ($.validator) {
  $.validator.setDefaults({
    errorElement: 'div',
    errorPlacement: function (error, element) {
      error.addClass('invalid-feedback');
      // 错误提示信息直接放在元素后面，或者放在.input-group、.check-group后面
      var group = $(element).parent('.input-group');
      if (group.length === 0) {
        group = $(element).parent('.check-group');
      }
      if (group.length === 0) {
        group = $(element).parent().parent('.check-group');
      }
      group.length > 0 ? group.after(error) : element.after(error);
    },
    highlight: function (element, errorClass, validClass) {
      $(element).closest('.form-control').addClass('is-invalid');
      // $( element ).closest( '.form-group' ).addClass( 'has-danger' );
    },
    unhighlight: function (element, errorClass, validClass) {
      $(element).closest('.form-control').removeClass('is-invalid');
      // $( element ).closest( '.form-group' ).removeClass( 'has-danger' );
    }
  });
  // 日期验证 2008-01-01 或 2008-01-01 08:00:00 或 2008-01-01T08:00:00
  $.validator.addMethod('date', function (value, element) {
    return this.optional(element) || /^\d{4}\-(0[1-9]|1[012])\-(0[1-9]|[12][0-9]|3[01])([ T]([01][0-9]|2[0-3])\:[0-5][0-9]\:[0-5][0-9])?$/.test(value);
  });
  // 覆盖 step 方法。因为日期控件flatpickr在手机浏览器下会自动给input表单加上step参数，导致验证失败。另外这个校验基本没有应用场合。
  $.validator.methods.step = function (value, element) {
    return true;
  };
}

