package com.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 隐私脱敏工具类
 */
public class PrivacyMasker {

    private static final Pattern PHONE_IN_TEXT = Pattern.compile(
            "(?<!\\d)(?:1[3-9]\\d(?:[ -]?\\d){8}|0\\d{2,3}-?\\d{7,8})(?!\\d)");

    /**
     * 姓名脱敏：只显示第一个字，后面显示*
     * 兼容中文名、英文名和其他特殊情况
     *
     * @param name 原始姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return name;
        }

        // 单字名直接返回
        if (trimmed.length() == 1) {
            return trimmed;
        }

        // 多字名：保留第一个字符，其余用*替换
        StringBuilder masked = new StringBuilder();
        masked.append(trimmed.charAt(0));

        // 根据名字长度决定*的数量
        int starCount = Math.min(trimmed.length() - 1, 3); // 最多3个*
        for (int i = 0; i < starCount; i++) {
            masked.append('*');
        }

        return masked.toString();
    }

    /**
     * 手机号脱敏：开头3位和最后4位正常显示，中间显示*
     * 兼容11位中国大陆手机号和其他格式
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // 移除所有非数字字符（空格、横线等）
        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.isEmpty()) {
            return phone; // 如果没有数字，返回原值
        }

        // 少于7位的号码，只显示前3位
        if (digits.length() < 7) {
            if (digits.length() <= 3) {
                return digits; // 3位及以下直接返回
            }
            return digits.substring(0, 3) + "****";
        }

        // 7位及以上：显示前3位和后4位，中间用*填充
        String prefix = digits.substring(0, 3);
        String suffix = digits.substring(digits.length() - 4);

        // 中间*的数量 = 总长度 - 7
        int middleLength = digits.length() - 7;
        StringBuilder masked = new StringBuilder(prefix);

        // 至少显示4个*
        int starCount = Math.max(4, middleLength);
        for (int i = 0; i < starCount; i++) {
            masked.append('*');
        }

        masked.append(suffix);
        return masked.toString();
    }

    /**
     * 脱敏文本中出现的手机号/固话，适用于电话混在地址行里的场景。
     */
    public static String maskPhonesInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = PHONE_IN_TEXT.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskPhone(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 组合脱敏：姓名 + 手机号
     *
     * @param nameAndPhone 原始字符串，格式如 "张三 13800138000"
     * @return 脱敏后的字符串
     */
    public static String maskNameAndPhone(String nameAndPhone) {
        if (nameAndPhone == null || nameAndPhone.isEmpty()) {
            return nameAndPhone;
        }

        // 尝试分割姓名和手机号
        String[] parts = nameAndPhone.split("\\s+");

        if (parts.length == 0) {
            return nameAndPhone;
        }

        if (parts.length == 1) {
            // 姓名和手机号可能粘在一起，例如“张三13800138000”。
            String part = parts[0];
            return part.matches(".*\\d.*") ? maskPhonesInText(part) : maskName(part);
        }

        // 多个部分：第一个按姓名处理，后续任何位置出现的电话都要脱敏。
        StringBuilder result = new StringBuilder();
        result.append(maskName(parts[0]));

        for (int i = 1; i < parts.length; i++) {
            result.append(" ").append(maskPhonesInText(parts[i]));
        }

        return result.toString();
    }

    /**
     * 收件人信息脱敏：姓名和手机号都脱敏
     */
    public static String maskRecipientInfo(String info) {
        return maskNameAndPhone(info);
    }

    /**
     * 发件人信息脱敏：姓名和手机号都脱敏
     */
    public static String maskSenderInfo(String info) {
        return maskNameAndPhone(info);
    }
}
